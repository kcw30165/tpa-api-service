package com.bct.ngtpa.apiservice.adapter.out.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionLifecycleService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.StartAuthFlowSessionResult;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented tests for {@link RegistrationAuthFlowSessionAdapter}. The reflective
 * extractSessionIdValue(...) path is exercised with REAL StartAuthFlowSessionResult/
 * AuthFlowSession/AuthFlowSessionId instances so it takes the confirmed real accessor chain:
 * result.session() -> session.authFlowSessionId() -> id.value() (rounds 46-47).
 */
class RegistrationAuthFlowSessionAdapterTest {

  private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");

  private final AuthFlowSessionLifecycleService lifecycleService =
      mock(AuthFlowSessionLifecycleService.class);
  private final RegistrationAuthFlowContextProtectorPort contextProtector =
      mock(RegistrationAuthFlowContextProtectorPort.class);

  private RegistrationAuthFlowSessionAdapter newAdapter() {
    return new RegistrationAuthFlowSessionAdapter(
        lifecycleService, 1800, "reg_", 16, contextProtector);
  }

  private AuthFlowSession session() {
    return new AuthFlowSession(
        new AuthFlowSessionId("afs-reg-session-1"),
        AuthFlowScenario.REGISTRATION,
        AuthFlowStatus.OTP_SENT,
        "csrfhash",
        null,
        new AuthFlowSessionData("email", "j***@example.com", Map.of()),
        NOW,
        NOW,
        NOW.plusSeconds(1800));
  }

  // ----- constructor guards ----------------------------------------------------

  @Test
  void nonPositiveTtlThrows() {
    assertThatThrownBy(
            () ->
                new RegistrationAuthFlowSessionAdapter(
                    lifecycleService, 0, "reg_", 16, contextProtector))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void blankPrefixThrows() {
    assertThatThrownBy(
            () ->
                new RegistrationAuthFlowSessionAdapter(
                    lifecycleService, 1800, "   ", 16, contextProtector))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void tooLowEntropyThrows() {
    assertThatThrownBy(
            () ->
                new RegistrationAuthFlowSessionAdapter(
                    lifecycleService, 1800, "reg_", 8, contextProtector))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nullLifecycleServiceThrows() {
    assertThatThrownBy(
            () -> new RegistrationAuthFlowSessionAdapter(null, 1800, "reg_", 16, contextProtector))
        .isInstanceOf(NullPointerException.class);
  }

  // ----- createRegistrationSession (2-arg) ------------------------------------

  @Test
  void createRegistrationSessionExtractsRealSessionIdViaReflection() {
    when(lifecycleService.start(any()))
        .thenReturn(Mono.just(new StartAuthFlowSessionResult(session(), 1800)));
    StepVerifier.create(
            newAdapter()
                .createRegistrationSession("j***@example.com", RegistrationContactType.EMAIL))
        .assertNext(sessionId -> assertThat(sessionId).isEqualTo("afs-reg-session-1"))
        .verifyComplete();
  }

  @Test
  void createRegistrationSessionWithNullContactTypeSucceeds() {
    when(lifecycleService.start(any()))
        .thenReturn(Mono.just(new StartAuthFlowSessionResult(session(), 1800)));
    StepVerifier.create(newAdapter().createRegistrationSession("j***@example.com", null))
        .assertNext(sessionId -> assertThat(sessionId).isEqualTo("afs-reg-session-1"))
        .verifyComplete();
  }

  // ----- updateOtpChallenge / markOtpVerified ---------------------------------

  @Test
  void updateOtpChallengeCompletes() {
    when(lifecycleService.updateActiveOtpChallenge(any())).thenReturn(Mono.just(session()));
    StepVerifier.create(
            newAdapter()
                .updateOtpChallenge(
                    "afs-reg-session-1",
                    "challenge-1",
                    RegistrationContactType.EMAIL,
                    "j***@example.com"))
        .verifyComplete();
  }

  @Test
  void markOtpVerifiedCompletes() {
    when(lifecycleService.updateStatus(any())).thenReturn(Mono.just(session()));
    StepVerifier.create(newAdapter().markOtpVerified("afs-reg-session-1")).verifyComplete();
  }

  // ----- createRegistrationSession (3-arg, context-protecting overload) -------

  @Test
  void createRegistrationSessionWithContextEncryptsAndExtractsSessionId() {
    when(contextProtector.protect(any()))
        .thenReturn(new RegistrationAuthFlowEncryptedContext("ct", "kv", "AES_GCM_V1"));
    when(lifecycleService.start(any()))
        .thenReturn(Mono.just(new StartAuthFlowSessionResult(session(), 1800)));
    RegistrationAuthFlowContext context =
        new RegistrationAuthFlowContext(
            MemberIdentityType.GPN,
            "G12345678",
            "01/01/1990",
            RegistrationContactType.EMAIL,
            "test@example.com");
    StepVerifier.create(
            newAdapter()
                .createRegistrationSession(
                    "j***@example.com", RegistrationContactType.EMAIL, context))
        .assertNext(sessionId -> assertThat(sessionId).isEqualTo("afs-reg-session-1"))
        .verifyComplete();
  }
}
