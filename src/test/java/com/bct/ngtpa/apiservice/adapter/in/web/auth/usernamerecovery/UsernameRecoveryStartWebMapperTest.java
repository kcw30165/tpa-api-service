package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.dto.UsernameRecoverySchemeResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.dto.UsernameRecoveryStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper.UsernameRecoveryStartWebMapper;
import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoverySchemeProjection;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Web mapper tests for Username Recovery /start: public identity mapping (STAFF_NO -> STAFFNO,
 * fail-closed rejection), command inputs, and the approved Start next-step/data projections.
 */
class UsernameRecoveryStartWebMapperTest {

  private final UsernameRecoveryStartWebMapper mapper = new UsernameRecoveryStartWebMapper();

  @Test
  void staffNoPublicIdentityMapsExactlyToSharedStaffno() {
    var command = mapper.toCommand(request("STAFF_NO"), Locale.ENGLISH, "idem-1");
    assertThat(command.identityType()).isEqualTo(MemberIdentityType.STAFFNO);
  }

  @Test
  void gpnGpfsAndIdMapDirectly() {
    assertThat(mapper.toCommand(request("GPN"), Locale.ENGLISH, "idem-1").identityType())
        .isEqualTo(MemberIdentityType.GPN);
    assertThat(mapper.toCommand(request("GPFS"), Locale.ENGLISH, "idem-1").identityType())
        .isEqualTo(MemberIdentityType.GPFS);
    assertThat(mapper.toCommand(request("ID"), Locale.ENGLISH, "idem-1").identityType())
        .isEqualTo(MemberIdentityType.ID);
  }

  @Test
  void unknownBlankCaseMutatedOrPaddedIdentityValuesFailClosed() {
    List<String> invalid =
        Arrays.asList(
            null,
            "",
            "   ",
            "staff_no",
            "STAFFNO",
            "Staff_No",
            " STAFF_NO",
            "STAFF_NO ",
            "PAN",
            "IDCARD",
            "Unknown");

    for (String value : invalid) {
      assertThatThrownBy(() -> mapper.toCommand(request(value), Locale.ENGLISH, "idem-1"))
          .as("public identity type '%s' must fail closed", value)
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void startRequestMapsDobContactAndCaptchaWithoutCaptchaAnswer() {
    var request =
        new UsernameRecoveryStartRequest(
            "GPFS", "1234567890", "31/01/1980", "member@example.com", null, "cap_verify");
    var command = mapper.toCommand(request, Locale.forLanguageTag("en-HK"), "idem_abc123");

    assertThat(command.identityType()).isEqualTo(MemberIdentityType.GPFS);
    assertThat(command.identityValue()).isEqualTo("1234567890");
    assertThat(command.dateOfBirth()).isEqualTo("31/01/1980");
    assertThat(command.email()).isEqualTo("member@example.com");
    assertThat(command.mobile()).isNull();
    assertThat(command.captchaChallengeId()).isEqualTo("cap_verify");
    assertThat(command.language()).isEqualTo(Locale.forLanguageTag("en-HK"));
    assertThat(command.idempotencyKey()).isEqualTo("idem_abc123");
    assertThat(command).isInstanceOf(UsernameRecoveryStartCommand.class);
    assertThat(command.toString())
        .doesNotContain(
            "1234567890", "31/01/1980", "member@example.com", "cap_verify", "idem_abc123");
  }

  @Test
  void nullStartRequestFailsClosed() {
    assertThatThrownBy(() -> mapper.toCommand(null, Locale.ENGLISH, "idem-1"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void sessionCreatedProjectsSelectAccountWithCsrfExpiryAndPreservedCandidateOrder() {
    var result =
        new UsernameRecoveryStartResult(
            UsernameRecoveryStartResult.Outcome.SESSION_CREATED,
            "csrf-value",
            600,
            List.of(
                new UsernameRecoverySchemeProjection(
                    new UsernameRecoverySchemeRef("sch_opaque-1"), "DB", "DBA", true),
                new UsernameRecoverySchemeProjection(
                    new UsernameRecoverySchemeRef("sch_opaque-2"), "DB", "DBB", false)));

    var response = mapper.toResult(result);

    assertThat(response.nextStep()).isEqualTo(WorkflowNextStep.SELECT_ACCOUNT);
    assertThat(response.data()).isNotNull();
    assertThat(response.data().csrfToken()).isEqualTo("csrf-value");
    assertThat(response.data().expiresInSeconds()).isEqualTo(600);
    assertThat(response.data().schemes())
        .extracting(UsernameRecoverySchemeResponse::schemeRef)
        .containsExactly("sch_opaque-1", "sch_opaque-2");
    assertThat(response.data().schemes())
        .extracting(UsernameRecoverySchemeResponse::accountEnv)
        .containsExactly("DB", "DB");
    assertThat(response.data().schemes())
        .extracting(UsernameRecoverySchemeResponse::trustCode)
        .containsExactly("DBA", "DBB");
    assertThat(response.data().schemes())
        .extracting(UsernameRecoverySchemeResponse::isRegistered)
        .containsExactly(true, false);
  }

  @Test
  void memberNotFoundProjectsRestartFlowWithNullData() {
    var result =
        new UsernameRecoveryStartResult(
            UsernameRecoveryStartResult.Outcome.MEMBER_NOT_FOUND, null, null, List.of());

    var response = mapper.toResult(result);

    assertThat(response.nextStep()).isEqualTo(WorkflowNextStep.RESTART_FLOW);
    assertThat(response.data()).isNull();
  }

  @Test
  void zeroRegisteredProjectsRestartFlowWithNullData() {
    var result =
        new UsernameRecoveryStartResult(
            UsernameRecoveryStartResult.Outcome.ZERO_REGISTERED,
            null,
            null,
            List.of(new UsernameRecoverySchemeProjection(null, "DB", "DBA", false)));

    var response = mapper.toResult(result);

    assertThat(response.nextStep()).isEqualTo(WorkflowNextStep.RESTART_FLOW);
    assertThat(response.data()).isNull();
  }

  @Test
  void nullStartResultFailsClosed() {
    assertThatThrownBy(() -> mapper.toResult(null)).isInstanceOf(NullPointerException.class);
  }

  private static UsernameRecoveryStartRequest request(String identityType) {
    return new UsernameRecoveryStartRequest(
        identityType, "3030609", "04/07/1978", "member@example.com", null, "cap_verify");
  }
}
