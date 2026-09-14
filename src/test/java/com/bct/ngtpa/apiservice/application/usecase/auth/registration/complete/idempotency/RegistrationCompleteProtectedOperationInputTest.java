package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SelectedTrust;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.UsernameCheckState;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.CompleteRegistrationCommand;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RegistrationCompleteProtectedOperationInputTest {
  private static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");
  private static final AuthFlowSessionId SESSION_ID = new AuthFlowSessionId("afs-safe");

  @Test
  void carriesOnlyAuthorizedTransientInputsAndRedactsEveryProtectedValue() {
    var input =
        new RegistrationCompleteProtectedOperationInput(
            command(SESSION_ID),
            new RegistrationAuthFlowContext(
                MemberIdentityType.ID,
                "A1234567",
                "01/01/1980",
                RegistrationContactType.EMAIL,
                "member@example.com"),
            new UsernameCheckState(new SelectedTrust("JPM", "OI"), "member1", NOW, null),
            session(SESSION_ID));

    assertThat(input.command().authFlowSessionId()).isEqualTo(SESSION_ID);
    assertThat(input.usernameCheckState().selectedTrust().trustCode()).isEqualTo("JPM");
    assertThat(input.toString())
        .contains(
            "command=<redacted>",
            "registrationContext=<redacted>",
            "usernameCheckState=<redacted>",
            "refreshedSession=<redacted>")
        .doesNotContain(
            "afs-safe",
            "csrf-secret",
            "idem-safe",
            "Password1!",
            "A1234567",
            "01/01/1980",
            "member@example.com",
            "member1",
            "JPM",
            "OI");
  }

  @Test
  void rejectsMissingAuthorizedInputs() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new RegistrationCompleteProtectedOperationInput(
                    null, context(), usernameState(), session(SESSION_ID)));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new RegistrationCompleteProtectedOperationInput(
                    command(SESSION_ID), null, usernameState(), session(SESSION_ID)));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new RegistrationCompleteProtectedOperationInput(
                    command(SESSION_ID), context(), null, session(SESSION_ID)));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new RegistrationCompleteProtectedOperationInput(
                    command(SESSION_ID), context(), usernameState(), null));
  }

  @Test
  void rejectsARefreshedSessionFromAnotherFlow() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new RegistrationCompleteProtectedOperationInput(
                    command(SESSION_ID),
                    context(),
                    usernameState(),
                    session(new AuthFlowSessionId("afs-other"))))
        .withMessage("completion command and refreshed session do not match");
  }

  private static CompleteRegistrationCommand command(AuthFlowSessionId sessionId) {
    return new CompleteRegistrationCommand(
        sessionId,
        "csrf-secret",
        new RegistrationIdempotencyKey("idem-safe"),
        "Password1!",
        "Password1!",
        "https://portal.test",
        "https://portal.test/registration",
        "en",
        "req-safe",
        Duration.ofMinutes(5));
  }

  private static RegistrationAuthFlowContext context() {
    return new RegistrationAuthFlowContext(
        MemberIdentityType.ID,
        "A1234567",
        "01/01/1980",
        RegistrationContactType.EMAIL,
        "member@example.com");
  }

  private static UsernameCheckState usernameState() {
    return new UsernameCheckState(new SelectedTrust("JPM", "OI"), "member1", NOW, null);
  }

  private static AuthFlowSession session(AuthFlowSessionId sessionId) {
    return new AuthFlowSession(
        sessionId,
        AuthFlowScenario.REGISTRATION,
        AuthFlowStatus.USERNAME_VERIFIED,
        "csrf-hash",
        "otp-safe",
        new AuthFlowSessionData("EMAIL", "m***@example.com", Map.of()),
        NOW.minusSeconds(60),
        NOW,
        NOW.plusSeconds(1800));
  }
}
