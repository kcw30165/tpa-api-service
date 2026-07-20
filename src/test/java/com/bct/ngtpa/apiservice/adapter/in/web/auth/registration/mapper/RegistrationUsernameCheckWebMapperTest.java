package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationSelectedTrustRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationUsernameCheckRequest;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.CheckRegistrationUsernameResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.CheckRegistrationUsernameResultStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegistrationUsernameCheckWebMapperTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RegistrationUsernameCheckWebMapper mapper = new RegistrationUsernameCheckWebMapper();

    @Test
    void mapsRequestToCommandWithoutInternalFields() {
        var command = mapper.toCommand(
                new RegistrationUsernameCheckRequest(new RegistrationSelectedTrustRequest("TL", "ENV1"), "abc1@"),
                "afs_abc",
                "csrf_abc",
                "req_abc",
                "en",
                "https://portal.example",
                "https://portal.example/page");

        assertThat(command.authFlowSessionId().value()).isEqualTo("afs_abc");
        assertThat(command.csrfToken()).isEqualTo("csrf_abc");
        assertThat(command.selectedTrust().trustCode()).isEqualTo("TL");
        assertThat(command.selectedTrust().accountEnv()).isEqualTo("ENV1");
        assertThat(command.username()).isEqualTo("abc1@");
        assertThat(command.toString()).doesNotContain("afs_abc", "csrf_abc", "abc1@");
    }

    @Test
    void mapsDownstreamRejectedStatusToContractText() throws Exception {
        var response = mapper.toMutationResponse(new CheckRegistrationUsernameResult(
                CheckRegistrationUsernameResultStatus.DOWNSTREAM_REJECTED,
                WorkflowNextStep.USERNAME_CHECK,
                List.of(),
                List.of(error("err.registration.username.isRegistered"))));

        JsonNode json = objectMapper.valueToTree(response);
        assertThat(json.get("status").asText()).isEqualTo("BUSINESS_REJECTED");
        assertThat(json.at("/errors/0/code").asText()).isEqualTo("err.registration.username.isRegistered");
        assertThat(json.toString()).doesNotContain("err.tpa.username.taken");
    }

    @Test
    void requestDtoDoesNotExposePolicyNoCertNoAndRedactsUsername() throws Exception {
        var request = new RegistrationUsernameCheckRequest(new RegistrationSelectedTrustRequest("TL", "ENV1"), "abc1@");
        JsonNode json = objectMapper.valueToTree(request);

        assertThat(json.toString()).doesNotContain("policyNo", "certNo");
        assertThat(request.toString()).doesNotContain("abc1@");
    }

    private static WorkflowErrorDescriptor error(String code) {
        return new WorkflowErrorDescriptor(WorkflowErrorType.FORM, code, null, List.of("PAGE"), WorkflowErrorSeverity.ERROR, "SERVER");
    }
}
