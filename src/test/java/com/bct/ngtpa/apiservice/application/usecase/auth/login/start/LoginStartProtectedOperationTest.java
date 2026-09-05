package com.bct.ngtpa.apiservice.application.usecase.auth.login.start;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationResult;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.login.LoginCredentialDestination;
import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthContext;
import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthDestination;
import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.port.in.CreateAuthFlowSessionCsrfUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CredentialAuthenticationPort;
import com.bct.ngtpa.apiservice.application.port.out.LoginPreAuthContextProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.preauth.LoginPreAuthContextFactory;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionLifecycleService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CreateAuthFlowSessionCsrfResult;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.StartAuthFlowSessionCommand;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.StartAuthFlowSessionResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LoginStartProtectedOperationTest {
  private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private final LoginPreAuthContextFactory contexts = mock(LoginPreAuthContextFactory.class);
  private final LoginPreAuthContextProtectorPort protector =
      mock(LoginPreAuthContextProtectorPort.class);
  private final AuthFlowSessionLifecycleService sessions =
      mock(AuthFlowSessionLifecycleService.class);
  private final CreateAuthFlowSessionCsrfUseCase csrf =
      mock(CreateAuthFlowSessionCsrfUseCase.class);

  @Test
  void mapsEachDeterministicCredentialRejectionWithoutProtectedStateWork() {
    assertRejection(
        CredentialAuthenticationStatus.INCORRECT_PASSWORD, StartLoginStatus.INCORRECT_PASSWORD);
    assertRejection(
        CredentialAuthenticationStatus.INCORRECT_USERNAME, StartLoginStatus.INCORRECT_USERNAME);
    assertRejection(
        CredentialAuthenticationStatus.INVALID_ACCOUNT, StartLoginStatus.INVALID_ACCOUNT);
    assertRejection(CredentialAuthenticationStatus.ACCOUNT_LOCKED, StartLoginStatus.ACCOUNT_LOCKED);
  }

  @Test
  void preservesUnavailableAsIndeterminateResult() {
    CredentialAuthenticationPort credentials =
        command -> Mono.just(result(CredentialAuthenticationStatus.UNAVAILABLE, null));
    StepVerifier.create(operation(credentials).execute(command()))
        .assertNext(
            value -> {
              assertThat(value.status()).isEqualTo(StartLoginStatus.UNAVAILABLE);
              assertThat(value.authFlowSessionId()).isNull();
              assertThat(value.csrfToken()).isNull();
              assertThat(value.replayCommitRef()).isNull();
            })
        .verifyComplete();
    verifyNoProtectedWork();
  }

  @Test
  void authenticatedCredentialsCreateExactProtectedSessionAndCsrfResult() {
    CredentialAuthenticationPort credentials = mock(CredentialAuthenticationPort.class);
    CredentialAuthenticationResult authenticated = authenticated();
    LoginPreAuthContext context = context();
    LoginPreAuthEncryptedContext encrypted =
        new LoginPreAuthEncryptedContext("v1", "AES/GCM/NoPadding", "iv-value", "cipher-value");
    AuthFlowSession session = mock(AuthFlowSession.class);
    AuthFlowSessionId sessionId = new AuthFlowSessionId("afs_abcdefghijklmnopqrstuvwx");
    when(session.authFlowSessionId()).thenReturn(sessionId);
    when(credentials.authenticate(command())).thenReturn(Mono.just(authenticated));
    when(contexts.create(authenticated, NOW)).thenReturn(context);
    when(protector.protect(context)).thenReturn(encrypted);
    when(sessions.start(any())).thenReturn(Mono.just(new StartAuthFlowSessionResult(session, 300)));
    when(csrf.createForSession(any()))
        .thenReturn(
            Mono.just(new CreateAuthFlowSessionCsrfResult("raw-csrf", NOW.plusSeconds(300), 300)));

    StepVerifier.create(operation(credentials).execute(command()))
        .assertNext(
            value -> {
              assertThat(value.status()).isEqualTo(StartLoginStatus.STARTED);
              assertThat(value.authFlowSessionId()).isEqualTo(sessionId.value());
              assertThat(value.csrfToken()).isEqualTo("raw-csrf");
              assertThat(value.expiresInSeconds()).isEqualTo(300);
              assertThat(value.failCount()).isEqualTo(0);
              assertThat(value.replayCommitRef()).isNull();
              assertThat(value.destinations())
                  .extracting(LoginDestinationChoice::destinationId)
                  .containsExactly("dst_first", "dst_second");
            })
        .verifyComplete();

    ArgumentCaptor<StartAuthFlowSessionCommand> start =
        ArgumentCaptor.forClass(StartAuthFlowSessionCommand.class);
    verify(sessions).start(start.capture());
    assertThat(start.getValue().scenario()).isEqualTo(AuthFlowScenario.LOGIN);
    assertThat(start.getValue().initialStatus()).isEqualTo(AuthFlowStatus.STARTED);
    assertThat(start.getValue().ttlPolicy().enabled()).isTrue();
    assertThat(start.getValue().ttlPolicy().required()).isTrue();
    assertThat(start.getValue().ttlPolicy().ttlSeconds()).isEqualTo(300);
    assertThat(start.getValue().sessionIdPrefix()).isEqualTo("afs_");
    assertThat(start.getValue().sessionIdEntropyBytes()).isEqualTo(24);
    assertThat(start.getValue().csrfTokenHash()).isNull();
    assertThat(start.getValue().activeOtpChallengeId()).isNull();
    assertThat(start.getValue().correlationId()).isEqualTo("req-1");
    assertThat(start.getValue().data().attributes())
        .containsEntry("loginContext.keyVersion", "v1")
        .containsEntry("loginContext.algorithm", "AES/GCM/NoPadding")
        .containsEntry("loginContext.iv", "iv-value")
        .containsEntry("loginContext.ciphertext", "cipher-value");

    var csrfCommand =
        ArgumentCaptor.forClass(
            com.bct.ngtpa.apiservice.application.usecase.session.authflow
                .CreateAuthFlowSessionCsrfCommand.class);
    verify(csrf).createForSession(csrfCommand.capture());
    assertThat(csrfCommand.getValue().authFlowSessionId()).isEqualTo(sessionId);
    assertThat(csrfCommand.getValue().now()).isEqualTo(NOW);
    assertThat(csrfCommand.getValue().ttlSeconds()).isEqualTo(300);
    assertThat(csrfCommand.getValue().correlationId()).isEqualTo("req-1");
    var ordered = inOrder(sessions, csrf);
    ordered.verify(sessions).start(any());
    ordered.verify(csrf).createForSession(any());
  }

  @Test
  void emptyAuthenticationResultFailsClosed() {
    StepVerifier.create(operation(command -> Mono.empty()).execute(command()))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Credential authentication result is unavailable"))
        .verify();
    verifyNoProtectedWork();
  }

  @Test
  void propagatesCredentialProtectionFailureSynchronously() {
    CredentialAuthenticationResult authenticated = authenticated();
    LoginPreAuthContext context = context();
    when(contexts.create(authenticated, NOW)).thenReturn(context);
    when(protector.protect(context)).thenThrow(new IllegalStateException("protection-failure"));

    assertThatThrownBy(
            () -> operation(command -> Mono.just(authenticated)).execute(command()).block())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("protection-failure");
  }

  @Test
  void propagatesSessionFailureReactively() {
    CredentialAuthenticationResult authenticated = authenticated();
    LoginPreAuthContext context = context();
    when(contexts.create(authenticated, NOW)).thenReturn(context);
    when(protector.protect(context))
        .thenReturn(new LoginPreAuthEncryptedContext("v1", "AES", "iv", "cipher"));
    when(sessions.start(any()))
        .thenReturn(Mono.error(new IllegalStateException("session-failure")));

    assertFailure(operation(command -> Mono.just(authenticated)), "session-failure");
  }

  @Test
  void propagatesCsrfFailureReactively() {
    CredentialAuthenticationResult authenticated = authenticated();
    LoginPreAuthContext context = context();
    when(contexts.create(authenticated, NOW)).thenReturn(context);
    when(protector.protect(context))
        .thenReturn(new LoginPreAuthEncryptedContext("v1", "AES", "iv", "cipher"));
    AuthFlowSession session = mock(AuthFlowSession.class);
    when(session.authFlowSessionId())
        .thenReturn(new AuthFlowSessionId("afs_abcdefghijklmnopqrstuvwx"));
    when(sessions.start(any())).thenReturn(Mono.just(new StartAuthFlowSessionResult(session, 300)));
    when(csrf.createForSession(any()))
        .thenReturn(Mono.error(new IllegalStateException("csrf-failure")));

    assertFailure(operation(command -> Mono.just(authenticated)), "csrf-failure");
  }

  private void assertFailure(LoginStartProtectedOperation value, String message) {
    StepVerifier.create(value.execute(command())).expectErrorMessage(message).verify();
  }

  private void assertRejection(CredentialAuthenticationStatus source, StartLoginStatus expected) {
    StepVerifier.create(operation(command -> Mono.just(result(source, 3))).execute(command()))
        .assertNext(
            value -> {
              assertThat(value.status()).isEqualTo(expected);
              assertThat(value.failCount()).isEqualTo(3);
              assertThat(value.destinations()).isEmpty();
              assertThat(value.replayCommitRef()).isNull();
            })
        .verifyComplete();
    verifyNoProtectedWork();
  }

  private void verifyNoProtectedWork() {
    verify(contexts, never()).create(any(), any());
    verify(protector, never()).protect(any());
    verify(sessions, never()).start(any());
    verify(csrf, never()).createForSession(any());
  }

  private LoginStartProtectedOperation operation(CredentialAuthenticationPort credentials) {
    return new LoginStartProtectedOperation(
        credentials, contexts, protector, sessions, csrf, CLOCK, 300, true, "afs_", 24);
  }

  private CredentialAuthenticationCommand command() {
    return new CredentialAuthenticationCommand(
        "member01", "Secret123", "en-HK", "req-1", ActorUserRole.ROLE_USER_MBR);
  }

  private CredentialAuthenticationResult authenticated() {
    return new CredentialAuthenticationResult(
        CredentialAuthenticationStatus.AUTHENTICATED,
        "member01",
        "role_user",
        0,
        List.of(
            new LoginCredentialDestination("email", "first@example.com"),
            new LoginCredentialDestination("mobile", "91234567")),
        null);
  }

  private CredentialAuthenticationResult result(
      CredentialAuthenticationStatus status, Integer failCount) {
    return new CredentialAuthenticationResult(
        status, "member01", "role_user", failCount, List.of(), null);
  }

  private LoginPreAuthContext context() {
    var destinations = new LinkedHashMap<String, LoginPreAuthDestination>();
    destinations.put(
        "dst_first",
        new LoginPreAuthDestination("dst_first", "email", "first@example.com", "f***@example.com"));
    destinations.put(
        "dst_second", new LoginPreAuthDestination("dst_second", "sms", "91234567", "****4567"));
    return new LoginPreAuthContext("member01", "role_user", destinations, NOW, "v1");
  }
}
