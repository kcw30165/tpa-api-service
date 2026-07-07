package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationOtpResendWorkflowPayloadGuardTest {

    @Test
    void resendMapperUsesDirectWorkflowResultConstructionNotReflectionForPrimaryPath() throws Exception {
        Path mapper = Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/mapper/RegistrationOtpResendWebMapper.java");
        assertThat(Files.exists(mapper)).isTrue();
        String source = Files.readString(mapper);
        assertThat(source).contains(
                "workflowMutationResponseMapper.toMutationResponse(toWorkflowResult(result))",
                "new WorkflowResult<>(",
                "RegistrationWorkflowPageRefs.forNextStep(result.nextStep())",
                "data(result)");
    }
}
