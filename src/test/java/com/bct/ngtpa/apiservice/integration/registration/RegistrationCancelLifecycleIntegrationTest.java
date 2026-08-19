package com.bct.ngtpa.apiservice.integration.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.bct.ngtpa.apiservice.application.dto.auth.otp.*;
import com.bct.ngtpa.apiservice.application.dto.session.*;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.cancel.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.*;
import com.bct.ngtpa.apiservice.integration.registration.support.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import reactor.core.publisher.Mono;

@ExtendWith(RegistrationTestResultExtension.class)
class RegistrationCancelLifecycleIntegrationTest {
  static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");
  static final AuthFlowSessionId SID = new AuthFlowSessionId("afs-cancel-safe");
  AuthFlowSessionPort sessions = mock(AuthFlowSessionPort.class);
  OtpChallengePort otp = mock(OtpChallengePort.class);
  AuthFlowSessionCancellationPort cancellation = mock(AuthFlowSessionCancellationPort.class);
  CancelRegistrationService service;
  AuthFlowSession state;

  @BeforeEach
  void setup() {
    state = session(AuthFlowStatus.OTP_SENT, "csrf-safe");
    when(sessions.load(SID)).thenAnswer(i -> Mono.just(Optional.ofNullable(state)));
    when(otp.invalidateChallenge(any())).thenReturn(Mono.empty());
    when(cancellation.cancel(any(), any(), any()))
        .thenReturn(Mono.just(new SessionCancellationResult(SessionCancellationStatus.CANCELLED)));
    service =
        new CancelRegistrationService(
            sessions,
            new AuthFlowSessionCsrfService(sessions),
            otp,
            cancellation,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  @RegistrationTestCase("TC-069")
  void activeFlowCancelsAndInvalidatesCurrentOtp() {
    var r = service.cancel(command("csrf-safe")).block();
    assertThat(r.status()).isEqualTo(CancelRegistrationResultStatus.CANCELLED);
    assertThat(r.clearAuthFlowCookie()).isTrue();
    assertThat(r.clearXsrfCookie()).isTrue();
    verify(otp).invalidateChallenge(any(InvalidateOtpChallengeCommand.class));
    verify(cancellation).cancel(SID, AuthFlowSessionCancellationReason.USER_CANCELLED, NOW);
  }

  @Test
  @RegistrationTestCase("TC-070")
  void repeatedCancelIsStableAndDoesNotReviveSession() {
    state = session(AuthFlowStatus.CANCELLED, "csrf-safe");
    var r = service.cancel(command("csrf-safe")).block();
    assertThat(r.status()).isEqualTo(CancelRegistrationResultStatus.ALREADY_CANCELLED);
    assertThat(r.successfulCleanup()).isTrue();
    verifyNoInteractions(otp, cancellation);
  }

  @Test
  @RegistrationTestCase("TC-071")
  void missingOrInvalidCsrfDoesNotCancelActiveFlow() {
    var missing = service.cancel(command(null)).block();
    assertThat(missing.status()).isEqualTo(CancelRegistrationResultStatus.CSRF_INVALID);
    var invalid = service.cancel(command("wrong-csrf")).block();
    assertThat(invalid.status()).isEqualTo(CancelRegistrationResultStatus.CSRF_INVALID);
    verifyNoInteractions(otp, cancellation);
  }

  @Test
  @RegistrationTestCase("TC-072")
  void missingOrExpiredSessionReturnsSafeCleanup() {
    state = null;
    var missing = service.cancel(command("csrf-safe")).block();
    assertThat(missing.status()).isEqualTo(CancelRegistrationResultStatus.SESSION_NOT_FOUND);
    assertThat(missing.successfulCleanup()).isTrue();
    assertThat(missing.clearAuthFlowCookie()).isTrue();

    state = session(AuthFlowStatus.EXPIRED, "csrf-safe");
    var expired = service.cancel(command("csrf-safe")).block();
    assertThat(expired.status()).isEqualTo(CancelRegistrationResultStatus.ALREADY_TERMINAL);
    assertThat(expired.successfulCleanup()).isTrue();
    verifyNoInteractions(otp, cancellation);
  }

  CancelRegistrationCommand command(String csrf) {
    return new CancelRegistrationCommand(
        SID, csrf, "https://portal.test", "https://portal.test/register", "req-cancel");
  }

  AuthFlowSession session(AuthFlowStatus status, String raw) {
    return new AuthFlowSession(
        SID,
        AuthFlowScenario.REGISTRATION,
        status,
        sha256(raw),
        "otp-cancel-safe",
        AuthFlowSessionData.empty(),
        NOW.minusSeconds(60),
        NOW,
        NOW.plusSeconds(1800));
  }

  String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
