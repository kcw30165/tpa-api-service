package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryDestinationType;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoverySchemeProjection;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Aggregated sensitive-data audit of every Task 022 response DTO.
 *
 * <p>Verifies that serialized record components are limited to the approved public field allowlist
 * and that mapped outputs / {@code toString()} values never contain a username, raw destination,
 * session ID, cookie, raw CSRF hash, identity value, DOB, idempotency value, sender reference, or
 * exception text.
 */
class UsernameRecoveryWebMapperSensitiveDataTest {

  private static final Set<String> APPROVED_COMPONENTS =
      Set.of(
          "nextStep",
          "data",
          "csrfToken",
          "expiresInSeconds",
          "schemes",
          "schemeRef",
          "accountEnv",
          "trustCode",
          "isRegistered",
          "status",
          "maskedDestination",
          "destinationType",
          "requestAccepted");

  private final UsernameRecoveryStartWebMapper startMapper = new UsernameRecoveryStartWebMapper();
  private final UsernameRecoveryRequestWebMapper requestMapper =
      new UsernameRecoveryRequestWebMapper();
  private final UsernameRecoveryCancelWebMapper cancelMapper =
      new UsernameRecoveryCancelWebMapper();

  @Test
  void responseDtosExposeOnlyApprovedPublicComponents() {
    List<Class<?>> responseTypes =
        List.of(
            UsernameRecoveryWorkflowResultResponse.class,
            UsernameRecoveryStartDataResponse.class,
            UsernameRecoverySchemeResponse.class,
            UsernameRecoveryRequestDataResponse.class);

    for (Class<?> type : responseTypes) {
      for (RecordComponent component : type.getRecordComponents()) {
        assertThat(APPROVED_COMPONENTS)
            .as("%s must not expose component %s", type.getSimpleName(), component.getName())
            .contains(component.getName());
      }
    }
  }

  @Test
  void noForbiddenValuesAppearInMappedOutputsOrResponseToStrings() {
    String[] forbidden = {
      "member@example.com",
      "3030609",
      "04/07/1978",
      "cap_secret",
      "csrf_hash_secret",
      "raw-destination@example.com",
      "afs_opaque",
      "auth_flow_sid",
      "idem-123",
      "sender-ref-1",
      "sch_opaque-1",
      "NullPointerException",
      "IllegalStateException"
    };

    var startResult =
        new UsernameRecoveryStartResult(
            UsernameRecoveryStartResult.Outcome.SESSION_CREATED,
            "csrf_hash_secret",
            600,
            List.of(
                new UsernameRecoverySchemeProjection(
                    new UsernameRecoverySchemeRef("sch_opaque-1"), "DB", "DBA", true)));
    var requestResult =
        new UsernameRecoveryRequestResult(
            UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED,
            UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED,
            new UsernameRecoverySchemeRef("sch_opaque-1"),
            "raw-destination@example.com",
            UsernameRecoveryDestinationType.EMAIL);

    String startText = startMapper.toResult(startResult).toString();
    String requestText = requestMapper.toResult(requestResult).toString();
    String cancelText =
        cancelMapper
            .toResult(
                new UsernameRecoveryCancelResult(UsernameRecoveryCancelResult.Outcome.SUCCESS))
            .toString();

    for (String value : forbidden) {
      assertThat(startText).as("start output must not contain %s", value).doesNotContain(value);
      assertThat(requestText).as("request output must not contain %s", value).doesNotContain(value);
      assertThat(cancelText).as("cancel output must not contain %s", value).doesNotContain(value);
    }
  }

  @Test
  void startRequestRecordContainsNoCaptchaAnswerOrTransportValues() {
    assertThat(
            Arrays.stream(UsernameRecoveryStartRequest.class.getRecordComponents())
                .map(RecordComponent::getName))
        .doesNotContain("captchaAnswer", "idempotencyKey", "requestId", "language", "csrfToken");
  }
}
