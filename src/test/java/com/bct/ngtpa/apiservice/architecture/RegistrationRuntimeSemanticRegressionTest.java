package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationRuntimeSemanticRegressionTest {
    @Test void apimStartBusinessRejectionRetainsDownstreamStatus() throws Exception {
        String service=read("StartRegistrationService.java");
        String mapper=read("RegistrationStartWebMapper.java");
        assertThat(service).contains("UPSTREAM_BUSINESS_REJECTED")
                .contains("StartRegistrationResultStatus.DOWNSTREAM_REJECTED");
        assertThat(mapper).contains("ApiStatus.DOWNSTREAM_REJECTED")
                .contains("WorkflowErrorType.DOWNSTREAM_BUSINESS");
    }
    @Test void resendSendRateLimitIsBusinessOutcomeWithLocalizedMessage() throws Exception {
        String mapper=read("RegistrationOtpResendWebMapper.java");
        String controller=read("AuthRegistrationOtpController.java");
        assertThat(mapper).contains("SEND_RATE_LIMITED, MAX_RESEND_ATTEMPTS_REACHED -> WorkflowResultStatus.BUSINESS_FAILED")
                .contains("errorMessageResolver.resolve(error.code(), language");
        assertThat(controller).contains("toMutationResponse(result, acceptLanguage)");
    }
    private String read(String name) throws Exception {
        try(var paths=Files.walk(Path.of("src/main/java"))){
            Path path=paths.filter(p->p.getFileName().toString().equals(name)).findFirst().orElseThrow();
            return Files.readString(path);
        }
    }
}
