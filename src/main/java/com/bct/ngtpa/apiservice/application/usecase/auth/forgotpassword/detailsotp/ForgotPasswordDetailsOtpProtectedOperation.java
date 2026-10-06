package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp;

import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_ALGORITHM;
import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_CIPHERTEXT;
import static com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContextAttributeNames.CONTEXT_KEY_VERSION;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordProtectedPhase;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.CreateOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.CreateOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChannel;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpScenario;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryCommand;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryResult;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryScenario;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryStatus;
import com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryType;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordOtpCommitStatePort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordOtpSessionBindingPort;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsStatus;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpBindingCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpCommitRetentionPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpCommitState;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpTemplates;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.SecureForgotPasswordOtpCommitRefGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Executes DETAILS_AND_OTP after shared idempotency ownership has been acquired. */
public final class ForgotPasswordDetailsOtpProtectedOperation {
  private static final ForgotPasswordOtpTemplates TEMPLATES = ForgotPasswordOtpTemplates.approved();
  private final SecureForgotPasswordOtpCommitRefGenerator references;
  private final AuthFlowSessionMutationLockPort locks;
  private final AuthFlowSessionPort sessions;
  private final ForgotPasswordAuthFlowContextProtectorPort protector;
  private final ForgotPasswordUnderLockDetailsVerifier details;
  private final OtpChallengePort otp;
  private final MessageDeliveryPort delivery;
  private final MaskingService masker;
  private final ForgotPasswordOtpSessionBindingPort binding;
  private final ForgotPasswordOtpCommitStatePort commitStates;
  private final ForgotPasswordOtpCommitRetentionPolicy retention;
  private final ForgotPasswordDetailsOtpLeasePolicy policy;
  private final Clock clock;
  private final Duration idempotencyTtl;

  public ForgotPasswordDetailsOtpProtectedOperation(
      SecureForgotPasswordOtpCommitRefGenerator references,
      AuthFlowSessionMutationLockPort locks,
      AuthFlowSessionPort sessions,
      ForgotPasswordAuthFlowContextProtectorPort protector,
      ForgotPasswordUnderLockDetailsVerifier details,
      OtpChallengePort otp,
      MessageDeliveryPort delivery,
      MaskingService masker,
      ForgotPasswordOtpSessionBindingPort binding,
      ForgotPasswordOtpCommitStatePort commitStates,
      ForgotPasswordOtpCommitRetentionPolicy retention,
      ForgotPasswordDetailsOtpLeasePolicy policy,
      Clock clock,
      Duration idempotencyTtl) {
    this.references = Objects.requireNonNull(references);
    this.locks = Objects.requireNonNull(locks);
    this.sessions = Objects.requireNonNull(sessions);
    this.protector = Objects.requireNonNull(protector);
    this.details = Objects.requireNonNull(details);
    this.otp = Objects.requireNonNull(otp);
    this.delivery = Objects.requireNonNull(delivery);
    this.masker = Objects.requireNonNull(masker);
    this.binding = Objects.requireNonNull(binding);
    this.commitStates = Objects.requireNonNull(commitStates);
    this.retention = Objects.requireNonNull(retention);
    this.policy = Objects.requireNonNull(policy);
    this.clock = Objects.requireNonNull(clock);
    this.idempotencyTtl = Objects.requireNonNull(idempotencyTtl);
  }

  public Mono<ForgotPasswordDetailsOtpProtectedExecutionResult> execute(
      ForgotPasswordDetailsOtpCommand command) {
    String reference = references.generate();
    return locks
        .acquire(command.sessionId(), policy.lease())
        .flatMap(handle -> handle.acquired() ? locked(command, reference, handle) : failure())
        .onErrorResume(ignored -> failure());
  }

  private Mono<ForgotPasswordDetailsOtpProtectedExecutionResult> locked(
      ForgotPasswordDetailsOtpCommand command, String reference, SessionLockHandle handle) {
    var budget = new ForgotPasswordMutationLeaseBudget(policy.lease(), System::nanoTime);
    Mono<ForgotPasswordDetailsOtpProtectedExecutionResult> work =
        sessions
            .loadSnapshot(command.sessionId())
            .flatMap(
                optional -> {
                  if (optional.isEmpty()) return failure();
                  AuthFlowSession session = optional.get().session();
                  if (session.scenario() != AuthFlowScenario.FORGOT_PASSWORD
                      || session.status() != AuthFlowStatus.IDENTITY_VERIFIED
                      || session.isExpired(clock.instant())) return failure();
                  ForgotPasswordAuthFlowContext context = decrypt(session);
                  if (context.phase() != ForgotPasswordProtectedPhase.IDENTITY_VERIFIED)
                    return failure();
                  return details
                      .verify(
                          handle,
                          session,
                          context,
                          command.dateOfBirth(),
                          command.email(),
                          command.mobile(),
                          command.requestId())
                      .flatMap(
                          result ->
                              result.status() == ForgotPasswordDetailsStatus.DETAILS_VERIFIED
                                  ? issue(command, reference, handle, budget)
                                  : Mono.just(
                                      new ForgotPasswordDetailsOtpProtectedExecutionResult(
                                          new ForgotPasswordDetailsOtpResult(
                                              ForgotPasswordDetailsOtpResult.Status
                                                  .BUSINESS_REJECTED,
                                              null,
                                              0,
                                              0,
                                              List.of("PAGE")),
                                          null)));
                });
    return Mono.usingWhen(
        Mono.just(handle),
        ignored -> work,
        ignored -> locks.release(handle).onErrorReturn(false).then(),
        (ignored, error) -> locks.release(handle).onErrorReturn(false).then(),
        ignored -> locks.release(handle).onErrorReturn(false).then());
  }

  private Mono<ForgotPasswordDetailsOtpProtectedExecutionResult> issue(
      ForgotPasswordDetailsOtpCommand command,
      String reference,
      SessionLockHandle handle,
      ForgotPasswordMutationLeaseBudget budget) {
    String channel = command.email() != null && !command.email().isBlank() ? "email" : "mobile";
    String destination = "email".equals(channel) ? command.email() : command.mobile();
    OtpChannel otpChannel = "email".equals(channel) ? OtpChannel.EMAIL : OtpChannel.SMS;
    return otp.createChallenge(
            new CreateOtpChallengeCommand(
                OtpScenario.FORGOT_PASSWORD,
                command.sessionId().value(),
                otpChannel,
                hex(destination),
                6,
                true,
                300,
                5,
                5,
                clock.instant()))
        .flatMap(
            challenge -> {
              if (!budget.active()) return failure();
              return delivery
                  .deliver(message(command, channel, destination, challenge))
                  .flatMap(
                      result ->
                          bindAndCommit(
                              command,
                              reference,
                              handle,
                              channel,
                              destination,
                              challenge,
                              result,
                              budget));
            });
  }

  private Mono<ForgotPasswordDetailsOtpProtectedExecutionResult> bindAndCommit(
      ForgotPasswordDetailsOtpCommand command,
      String reference,
      SessionLockHandle handle,
      String channel,
      String destination,
      CreateOtpChallengeResult challenge,
      MessageDeliveryResult deliveryResult,
      ForgotPasswordMutationLeaseBudget budget) {
    if (!budget.hasPersistenceBudget(policy.persistenceAllowance())) return failure();
    Instant now = clock.instant();
    var bind =
        new ForgotPasswordOtpBindingCommand(
            command.sessionId(),
            handle,
            deliveryResult.status(),
            challenge.otpChallengeId(),
            channel,
            destination,
            "email".equals(channel) ? masker.maskEmail(destination) : masker.maskMobile(destination),
            challenge.expiresAt(),
            now.plusSeconds(60),
            now);
    return binding
        .apply(bind)
        .flatMap(
            bound -> {
              if (deliveryResult.status() != MessageDeliveryStatus.ACCEPTED || !bound.bound()) {
                return Mono.error(
                    new IllegalStateException(
                        "DETAILS_AND_OTP delivery outcome is not safely completed"));
              }
              if (!budget.hasPersistenceBudget(policy.persistenceAllowance())) return failure();
              var state =
                  new ForgotPasswordOtpCommitState(
                      command.sessionId().value(),
                      challenge.otpChallengeId(),
                      now,
                      challenge.expiresAt());
              Duration ttl = retention.ttl(now, challenge.expiresAt(), now.plus(idempotencyTtl));
              return commitStates
                  .saveIfAbsent(reference, state, ttl)
                  .flatMap(saved -> commitStates.load(reference))
                  .flatMap(
                      found ->
                          found.isPresent() && found.get().equals(state)
                              ? Mono.just(
                                  new ForgotPasswordDetailsOtpProtectedExecutionResult(
                                      new ForgotPasswordDetailsOtpResult(
                                          ForgotPasswordDetailsOtpResult.Status.SUCCESS,
                                          "email".equals(channel)
                                              ? masker.maskEmail(destination)
                                              : masker.maskMobile(destination),
                                          challenge.expiresInSeconds(),
                                          60,
                                          List.of()),
                                      new ProtectedReplayReference(reference)))
                              : Mono.error(
                                  new IllegalStateException(
                                      "DETAILS_AND_OTP commit state conflict")));
            });
  }

  private MessageDeliveryCommand message(
      ForgotPasswordDetailsOtpCommand command,
      String channel,
      String destination,
      CreateOtpChallengeResult challenge) {
    return new MessageDeliveryCommand(
        MessageDeliveryType.FORGOT_PASSWORD_OTP,
        MessageDeliveryScenario.FORGOT_PASSWORD,
        "email".equals(channel) ? MessageDeliveryChannel.EMAIL : MessageDeliveryChannel.SMS,
        destination,
        "email".equals(channel) ? TEMPLATES.emailSubject(command.language()) : null,
        "email".equals(channel)
            ? TEMPLATES.emailBody(command.language())
            : TEMPLATES.sms(command.language()),
        null,
        Map.of(
            "code", challenge.rawCode(), "ttlValue", "5", "ttlUnit", ttlUnit(command.language())),
        command.language(),
        command.requestId());
  }

  private ForgotPasswordAuthFlowContext decrypt(AuthFlowSession session) {
    Map<String, String> attributes = session.data().attributes();
    return protector.unprotect(
        new ForgotPasswordAuthFlowEncryptedContext(
            attributes.get(CONTEXT_CIPHERTEXT),
            attributes.get(CONTEXT_KEY_VERSION),
            attributes.getOrDefault(
                CONTEXT_ALGORITHM, ForgotPasswordAuthFlowEncryptedContext.DEFAULT_ALGORITHM)));
  }

  private Mono<ForgotPasswordDetailsOtpProtectedExecutionResult> failure() {
    return Mono.just(
        new ForgotPasswordDetailsOtpProtectedExecutionResult(
            ForgotPasswordDetailsOtpResult.system(), null));
  }

  private String hex(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("hash unavailable");
    }
  }

  private String ttlUnit(String language) {
    return language != null && language.trim().replace('-', '_').equalsIgnoreCase("zh_HK")
        ? "分鐘"
        : "minutes";
  }
}
