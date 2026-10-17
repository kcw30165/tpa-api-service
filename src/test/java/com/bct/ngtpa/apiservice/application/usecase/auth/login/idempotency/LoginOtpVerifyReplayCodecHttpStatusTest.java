package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify.VerifyLoginOtpStatus;
import org.junit.jupiter.api.Test;

/**
 * Locks in the full HTTP status mapping for every {@link VerifyLoginOtpStatus} value.
 *
 * <p>Regression guard for the defect where {@code SYSTEM_FAILED} and {@code ALREADY_VERIFIED}
 * had no explicit case in {@link LoginOtpVerifyReplayCodec#httpStatus(VerifyLoginOtpStatus)} and
 * therefore fell through to the default throw, surfacing as an unexplained 500 on login OTP
 * verify replay capture/hydrate.
 */
class LoginOtpVerifyReplayCodecHttpStatusTest {

  @Test
  void mapsAccountsReadyTo200() {
    assertThat(LoginOtpVerifyReplayCodec.httpStatus(VerifyLoginOtpStatus.ACCOUNTS_READY))
        .isEqualTo(200);
  }

  @Test
  void mapsCsrfInvalidTo403() {
    assertThat(LoginOtpVerifyReplayCodec.httpStatus(VerifyLoginOtpStatus.CSRF_INVALID))
        .isEqualTo(403);
  }

  @Test
  void mapsVerifyAttemptsExceededTo429() {
    assertThat(LoginOtpVerifyReplayCodec.httpStatus(VerifyLoginOtpStatus.VERIFY_ATTEMPTS_EXCEEDED))
        .isEqualTo(429);
  }

  @Test
  void mapsSessionCreationFailedTo503() {
    assertThat(LoginOtpVerifyReplayCodec.httpStatus(VerifyLoginOtpStatus.SESSION_CREATION_FAILED))
        .isEqualTo(503);
  }

  @Test
  void mapsAccountListUnavailableTo502() {
    assertThat(LoginOtpVerifyReplayCodec.httpStatus(VerifyLoginOtpStatus.ACCOUNT_LIST_UNAVAILABLE))
        .isEqualTo(502);
  }

  @Test
  void mapsClientInputRejectionStatusesTo422() {
    assertThat(LoginOtpVerifyReplayCodec.httpStatus(VerifyLoginOtpStatus.ZERO_VALID_ACCOUNTS))
        .isEqualTo(422);
    assertThat(LoginOtpVerifyReplayCodec.httpStatus(VerifyLoginOtpStatus.INVALID)).isEqualTo(422);
    assertThat(LoginOtpVerifyReplayCodec.httpStatus(VerifyLoginOtpStatus.EXPIRED)).isEqualTo(422);
    assertThat(LoginOtpVerifyReplayCodec.httpStatus(VerifyLoginOtpStatus.AUTH_FLOW_INVALID))
        .isEqualTo(422);
  }

  @Test
  void mapsAlreadyVerifiedTo409() {
    assertThat(LoginOtpVerifyReplayCodec.httpStatus(VerifyLoginOtpStatus.ALREADY_VERIFIED))
        .isEqualTo(409);
  }

  @Test
  void mapsSystemFailedTo500() {
    assertThat(LoginOtpVerifyReplayCodec.httpStatus(VerifyLoginOtpStatus.SYSTEM_FAILED))
        .isEqualTo(500);
  }
}
