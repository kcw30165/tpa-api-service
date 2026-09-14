package com.bct.ngtpa.apiservice.application.usecase.auth.login.start;

import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationResult;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthContext;
import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.session.SessionTtlPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.port.in.CreateAuthFlowSessionCsrfUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CredentialAuthenticationPort;
import com.bct.ngtpa.apiservice.application.port.out.LoginPreAuthContextProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.preauth.LoginPreAuthContextFactory;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionLifecycleService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CreateAuthFlowSessionCsrfCommand;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.StartAuthFlowSessionCommand;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Establishes Login Start protected state without owning idempotency lifecycle transitions. */
public final class LoginStartProtectedOperation {
  private final CredentialAuthenticationPort credentials;
  private final LoginPreAuthContextFactory contexts;
  private final LoginPreAuthContextProtectorPort protector;
  private final AuthFlowSessionLifecycleService sessions;
  private final CreateAuthFlowSessionCsrfUseCase csrf;
  private final Clock clock;
  private final int ttl;
  private final boolean required;
  private final String prefix;
  private final int entropy;

  public LoginStartProtectedOperation(
      CredentialAuthenticationPort credentials,
      LoginPreAuthContextFactory contexts,
      LoginPreAuthContextProtectorPort protector,
      AuthFlowSessionLifecycleService sessions,
      CreateAuthFlowSessionCsrfUseCase csrf,
      Clock clock,
      int ttl,
      boolean required,
      String prefix,
      int entropy) {
    this.credentials = Objects.requireNonNull(credentials);
    this.contexts = Objects.requireNonNull(contexts);
    this.protector = Objects.requireNonNull(protector);
    this.sessions = Objects.requireNonNull(sessions);
    this.csrf = Objects.requireNonNull(csrf);
    this.clock = Objects.requireNonNull(clock);
    this.ttl = ttl;
    this.required = required;
    this.prefix = prefix;
    this.entropy = entropy;
  }

  public Mono<StartLoginResult> execute(CredentialAuthenticationCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return credentials
        .authenticate(command)
        .switchIfEmpty(
            Mono.error(
                new IllegalStateException("Credential authentication result is unavailable")))
        .flatMap(result -> establish(command, result));
  }

  private Mono<StartLoginResult> establish(
      CredentialAuthenticationCommand command, CredentialAuthenticationResult result) {
    Objects.requireNonNull(result, "Credential authentication result must not be null");
    if (result.status() != CredentialAuthenticationStatus.AUTHENTICATED) {
      return Mono.just(failure(result));
    }

    LoginPreAuthContext context = contexts.create(result, clock.instant());
    LoginPreAuthEncryptedContext encrypted = protector.protect(context);
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put("loginContext.keyVersion", encrypted.keyVersion());
    attributes.put("loginContext.algorithm", encrypted.algorithm());
    attributes.put("loginContext.iv", encrypted.iv());
    attributes.put("loginContext.ciphertext", encrypted.ciphertext());
    var data = new AuthFlowSessionData(null, null, attributes);
    var start =
        new StartAuthFlowSessionCommand(
            AuthFlowScenario.LOGIN,
            AuthFlowStatus.STARTED,
            new SessionTtlPolicy(true, required, ttl),
            prefix,
            entropy,
            data,
            null,
            null,
            command.correlationId());

    return sessions
        .start(start)
        .flatMap(
            created ->
                csrf.createForSession(
                        new CreateAuthFlowSessionCsrfCommand(
                            created.session().authFlowSessionId(),
                            clock.instant(),
                            ttl,
                            command.correlationId()))
                    .map(
                        token ->
                            new StartLoginResult(
                                StartLoginStatus.STARTED,
                                created.session().authFlowSessionId().value(),
                                token.rawToken(),
                                created.expiresInSeconds(),
                                publicChoices(context),
                                result.failCount(),
                                null)));
  }

  private List<LoginDestinationChoice> publicChoices(LoginPreAuthContext context) {
    return context.destinationsById().values().stream()
        .map(
            destination ->
                new LoginDestinationChoice(
                    destination.destinationId(),
                    destination.channel(),
                    destination.maskedDestination()))
        .toList();
  }

  private StartLoginResult failure(CredentialAuthenticationResult result) {
    StartLoginStatus status =
        switch (result.status()) {
          case INCORRECT_PASSWORD -> StartLoginStatus.INCORRECT_PASSWORD;
          case INCORRECT_USERNAME -> StartLoginStatus.INCORRECT_USERNAME;
          case INVALID_ACCOUNT -> StartLoginStatus.INVALID_ACCOUNT;
          case ACCOUNT_LOCKED -> StartLoginStatus.ACCOUNT_LOCKED;
          default -> StartLoginStatus.UNAVAILABLE;
        };
    return new StartLoginResult(status, null, null, null, List.of(), result.failCount(), null);
  }
}
