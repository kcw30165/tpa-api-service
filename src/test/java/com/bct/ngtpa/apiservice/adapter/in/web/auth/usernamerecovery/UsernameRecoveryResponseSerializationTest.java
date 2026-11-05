package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.dto.UsernameRecoveryRequestDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.dto.UsernameRecoverySchemeResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.dto.UsernameRecoveryStartDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.dto.UsernameRecoveryWorkflowResultResponse;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Serialization/public-contract audit of the Task 022 response DTOs.
 *
 * <p>Uses a plain {@link ObjectMapper} (default Jackson configuration, the repository's global
 * configuration) and verifies that every record serializes exactly the approved public JSON field
 * names and values, that null session keys are omitted (Jackson NON_NULL) while {@code schemeRef:
 * null} is preserved, and that no forbidden field name can leak.
 */
class UsernameRecoveryResponseSerializationTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void startSessionDataSerializesApprovedPublicFieldsOnly() throws Exception {
    var data =
        new UsernameRecoveryStartDataResponse(
            "csrf-value",
            600,
            List.of(
                new UsernameRecoverySchemeResponse("sch_opaque-1", "DB", "DBA", true),
                new UsernameRecoverySchemeResponse("sch_opaque-2", "DB", "DBB", false)));

    String json = objectMapper.writeValueAsString(data);

    assertThat(json)
        .contains("\"csrfToken\":\"csrf-value\"")
        .contains("\"expiresInSeconds\":600")
        .contains("\"schemes\"")
        .contains("\"schemeRef\":\"sch_opaque-1\"")
        .contains("\"schemeRef\":\"sch_opaque-2\"")
        .contains("\"accountEnv\":\"DB\"")
        .contains("\"trustCode\":\"DBA\"")
        .contains("\"trustCode\":\"DBB\"")
        .contains("\"isRegistered\":true")
        .contains("\"isRegistered\":false")
        .doesNotContain(
            "identityValue",
            "dateOfBirth",
            "email",
            "mobile",
            "captcha",
            "sessionId",
            "cookie",
            "idempotencyKey",
            "requestId");
  }

  @Test
  void startDataOmitsNullSessionKeysButKeepsNullSchemeRef() throws Exception {
    var data =
        new UsernameRecoveryStartDataResponse(
            null, null, List.of(new UsernameRecoverySchemeResponse(null, "DB", "DBA", false)));

    String json = objectMapper.writeValueAsString(data);

    assertThat(json)
        .contains("\"schemes\"")
        .contains("\"schemeRef\":null")
        .contains("\"accountEnv\":\"DB\"")
        .contains("\"trustCode\":\"DBA\"")
        .contains("\"isRegistered\":false")
        .doesNotContain("csrfToken", "expiresInSeconds");
  }

  @Test
  void requestDataSerializesApprovedPublicFieldsOnly() throws Exception {
    var data =
        new UsernameRecoveryRequestDataResponse(
            "PARTIALLY_REQUESTED", "sch_opaque-1", "p****c@ah.ozk", "EMAIL", true);

    String json = objectMapper.writeValueAsString(data);

    assertThat(json)
        .contains("\"status\":\"PARTIALLY_REQUESTED\"")
        .contains("\"schemeRef\":\"sch_opaque-1\"")
        .contains("\"maskedDestination\":\"p****c@ah.ozk\"")
        .contains("\"destinationType\":\"EMAIL\"")
        .contains("\"requestAccepted\":true")
        .doesNotContain("identityValue", "dateOfBirth", "sessionId", "cookie", "csrfToken");
  }

  @Test
  void workflowResultSerializesNextStepAndDataNullWithoutEmptyObject() throws Exception {
    String json =
        objectMapper.writeValueAsString(
            new UsernameRecoveryWorkflowResultResponse<>(WorkflowNextStep.RETURN_TO_LOGIN, null));

    assertThat(json)
        .contains("\"nextStep\":\"RETURN_TO_LOGIN\"")
        .contains("\"data\":null")
        .doesNotContain("{}");
  }

  @Test
  void startWorkflowResponseNeverLeaksSessionCookieOrCsrfHashMaterial() throws Exception {
    var data =
        new UsernameRecoveryStartDataResponse(
            "csrf-value",
            600,
            List.of(new UsernameRecoverySchemeResponse("sch_opaque-1", "DB", "DBA", true)));

    String json =
        objectMapper.writeValueAsString(
            new UsernameRecoveryWorkflowResultResponse<>(WorkflowNextStep.SELECT_ACCOUNT, data));

    assertThat(json)
        .contains("\"csrfToken\":\"csrf-value\"")
        .doesNotContain(
            "authFlowSessionId",
            "sessionId",
            "auth_flow_sid",
            "XSRF-TOKEN",
            "csrfHash",
            "username",
            "cookie",
            "rawDestination",
            "requestId");
  }
}
