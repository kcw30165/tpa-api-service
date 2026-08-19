package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Structural contract of the public /start request body record.
 *
 * <p>Locks the six approved public fields and the exclusion of captcha answer, idempotency, request
 * correlation, language, session, cookie, CSRF, Scheme-Ref, account, and transport values. Also
 * locks the redacted {@code toString()}.
 */
class UsernameRecoveryStartRequestTest {

  @Test
  void startRequestExposesExactlyTheSixApprovedPublicFields() {
    assertThat(
            Arrays.stream(UsernameRecoveryStartRequest.class.getRecordComponents())
                .map(RecordComponent::getName))
        .containsExactly(
            "identityType", "identityValue", "dateOfBirth", "email", "mobile", "captchaChallengeId")
        .doesNotContain(
            "captchaAnswer",
            "idempotencyKey",
            "requestId",
            "language",
            "lang",
            "csrfToken",
            "schemeRef",
            "authFlowSessionId",
            "sessionId",
            "cookie",
            "accountRef",
            "identityValueRaw");
  }

  @Test
  void startRequestToStringNeverDisclosesSensitiveValues() {
    var request =
        new UsernameRecoveryStartRequest(
            "STAFF_NO", "3030609", "04/07/1978", "member@example.com", null, "cap_secret");

    assertThat(request.toString())
        .contains("STAFF_NO")
        .doesNotContain("3030609", "04/07/1978", "member@example.com", "cap_secret");
  }
}
