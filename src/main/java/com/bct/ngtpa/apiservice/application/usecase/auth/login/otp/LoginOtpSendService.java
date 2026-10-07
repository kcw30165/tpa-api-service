package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp;

import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthContext;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.*;
import com.bct.ngtpa.apiservice.application.dto.message.*;
import com.bct.ngtpa.apiservice.application.port.in.SendLoginOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.service.security.ratelimit.DestinationBucketIdGenerator;
import com.bct.ngtpa.apiservice.application.service.security.ratelimit.DestinationType;
import java.time.*;
import java.util.*;
import reactor.core.publisher.Mono;

public final class LoginOtpSendService implements SendLoginOtpUseCase {
  private final LoginProtectedAuthFlowContextLoader contextLoader;
  private final LoginOtpDestinationResolver destinationResolver;
  private final OtpChallengePort otp;
  private final MessageDeliveryPort messages;
  private final LoginOtpSessionBindingPort sessions;
  private final DestinationBucketIdGenerator destinationBucketIds;
  private final Clock clock;
  private final LoginOtpSendPolicy policy;
  private final NoticeTemplateRegistryPort registryPort;

  public LoginOtpSendService(
      LoginProtectedAuthFlowContextLoader contextLoader,
      LoginOtpDestinationResolver destinationResolver,
      OtpChallengePort otp,
      MessageDeliveryPort messages,
      LoginOtpSessionBindingPort sessions,
      DestinationBucketIdGenerator destinationBucketIds,
      Clock clock,
      LoginOtpSendPolicy policy,
      NoticeTemplateRegistryPort registryPort) {
    this.contextLoader = Objects.requireNonNull(contextLoader);
    this.destinationResolver = Objects.requireNonNull(destinationResolver);
    this.otp = Objects.requireNonNull(otp);
    this.messages = Objects.requireNonNull(messages);
    this.sessions = Objects.requireNonNull(sessions);
    this.destinationBucketIds = Objects.requireNonNull(destinationBucketIds);
    this.clock = Objects.requireNonNull(clock);
    this.policy = Objects.requireNonNull(policy);
    this.registryPort = Objects.requireNonNull(registryPort);
  }

  public Mono<SendLoginOtpResult> send(SendLoginOtpCommand command) {
    Objects.requireNonNull(command);
    return contextLoader
        .load(
            new LoginProtectedAuthFlowContextCommand(
                command.authFlowSessionId(),
                command.csrfToken(),
                command.origin(),
                command.referer(),
                command.correlationId(),
                com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus.STARTED,
                false))
        .flatMap(
            loaded -> {
              if (!loaded.isValid())
                return Mono.just(
                    new SendLoginOtpResult(
                        loaded.status() == LoginProtectedAuthFlowContextStatus.CSRF_INVALID
                            ? SendLoginOtpStatus.CSRF_INVALID
                            : SendLoginOtpStatus.AUTH_FLOW_INVALID,
                        null,
                        null,
                        null,
                        null));
              ResolvedLoginOtpDestination destination;
              try {
                destination = destinationResolver.resolve(loaded.context(), command);
              } catch (InvalidLoginOtpDestinationException exception) {
                return Mono.just(
                    new SendLoginOtpResult(
                        SendLoginOtpStatus.INVALID_DESTINATION, null, null, null, null));
              }
              return createDeliverBind(command, destination, loaded.context());
            });
  }

  private Mono<SendLoginOtpResult> createDeliverBind(
      SendLoginOtpCommand command,
      ResolvedLoginOtpDestination destination,
      LoginPreAuthContext context) {
    Instant now = clock.instant();
    MessageDeliveryChannel channel = messageChannel(destination.channel());
    NoticeTemplateDefinition definition =
        registryPort.resolve(MessageDeliveryType.LOGIN_OTP, channel, command.acceptLanguage());
    CreateOtpChallengeCommand create =
        new CreateOtpChallengeCommand(
            OtpScenario.LOGIN,
            command.authFlowSessionId(),
            otpChannel(destination.channel()),
            destinationBucketIds.generateBucketId(
                destinationType(destination.channel()), destination.destination()),
            policy.length(),
            policy.numericOnly(),
            policy.ttlSeconds(),
            policy.maxVerifyAttempts(),
            policy.resendLimitEnabled() ? policy.maxResendAttempts() : 0,
            now);
    return otp.createChallenge(create)
        .flatMap(
            challenge -> {
              MessageDeliveryCommand delivery =
                  new MessageDeliveryCommand(
                      MessageDeliveryType.LOGIN_OTP,
                      MessageDeliveryScenario.LOGIN,
                      channel,
                      destination.destination(),
                      definition.subject(),
                      definition.body(),
                      "login-otp",
                      templateVariables(challenge.rawCode(), definition, context),
                      command.acceptLanguage(),
                      command.correlationId());
              return messages
                  .deliver(delivery)
                  .flatMap(
                      outcome -> {
                        if (outcome.status() == MessageDeliveryStatus.UNKNOWN_OUTCOME)
                          return otp.invalidateChallenge(
                                  new InvalidateOtpChallengeCommand(
                                      challenge.otpChallengeId(),
                                      OtpChallengeInvalidationReason.DELIVERY_FAILED,
                                      clock.instant()))
                              .thenReturn(
                                  new SendLoginOtpResult(
                                      SendLoginOtpStatus.UNKNOWN_OUTCOME, null, null, null, null));
                        if (outcome.status() == MessageDeliveryStatus.FAILED)
                          return otp.invalidateChallenge(
                                  new InvalidateOtpChallengeCommand(
                                      challenge.otpChallengeId(),
                                      OtpChallengeInvalidationReason.DELIVERY_FAILED,
                                      clock.instant()))
                              .thenReturn(
                                  new SendLoginOtpResult(
                                      SendLoginOtpStatus.DELIVERY_FAILED, null, null, null, null));
                        return sessions
                            .bind(
                                new LoginOtpSessionBindingCommand(
                                    command.authFlowSessionId(),
                                    null,
                                    challenge.otpChallengeId(),
                                    com.bct.ngtpa.apiservice.application.dto.session.authflow
                                        .AuthFlowStatus.STARTED,
                                    com.bct.ngtpa.apiservice.application.dto.session.authflow
                                        .AuthFlowStatus.OTP_SENT,
                                    destination.channel().name().toLowerCase(java.util.Locale.ROOT),
                                    destination.maskedDestination(),
                                    command.correlationId()))
                            .thenReturn(
                                new SendLoginOtpResult(
                                    SendLoginOtpStatus.SENT,
                                    destination.maskedDestination(),
                                    challenge.expiresInSeconds(),
                                    policy.resendCooldownSeconds(),
                                    null));
                      });
            });
  }

  private Map<String, String> templateVariables(
      String rawCode, NoticeTemplateDefinition definition, LoginPreAuthContext context) {
    var variables = new LinkedHashMap<String, String>();
    variables.put("code", rawCode);
    variables.put("ttlValue", Integer.toString(Math.max(1, policy.ttlSeconds() / 60)));
    if (definition.ttlUnit() != null) {
      variables.put("ttlUnit", definition.ttlUnit());
    }
    if (context.trustCode() != null) {
      variables.put("trustCode", context.trustCode());
    }
    if (context.schemeName() != null) {
      variables.put("schemeName", context.schemeName());
    }
    if (context.schemeNameChi() != null) {
      variables.put("schemeNameChi", context.schemeNameChi());
    }
    return Map.copyOf(variables);
  }

  private DestinationType destinationType(OtpChannel channel) {
    return channel == OtpChannel.EMAIL ? DestinationType.EMAIL : DestinationType.MOBILE;
  }

  private OtpChannel otpChannel(OtpChannel channel) {
    return channel == OtpChannel.EMAIL ? OtpChannel.EMAIL : OtpChannel.SMS;
  }

  private MessageDeliveryChannel messageChannel(OtpChannel channel) {
    return channel == OtpChannel.EMAIL ? MessageDeliveryChannel.EMAIL : MessageDeliveryChannel.SMS;
  }

  @Override
  public String toString() {
    return "LoginOtpSendService[policy=<configured>]";
  }
}
