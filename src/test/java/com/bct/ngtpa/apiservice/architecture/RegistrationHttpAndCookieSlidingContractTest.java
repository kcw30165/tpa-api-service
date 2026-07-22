package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationHttpAndCookieSlidingContractTest {

    @Test
    void captchaVerifyAndUsernameCheckUseRegistrationHttpTranslation() throws Exception {
        String captcha = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthCaptchaController.java"));
        String username = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthRegistrationUsernameController.java"));
        String mapper = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/mapper/RegistrationUsernameCheckWebMapper.java"));

        assertThat(captcha).contains("RegistrationHttpResponses.toResponse");
                assertThat(username).contains("RegistrationHttpResponses.toResponse");
                assertThat(mapper).contains("MutationResponse<RegistrationWorkflowResultResponse<RegistrationUsernameCheckResponse>>");
    }

    @Test
    void protectedRegistrationInteractionsReissueSlidingCookie() throws Exception {
        for (String file : new String[] {
                "AuthRegistrationOtpController.java",
                "AuthRegistrationUsernameController.java",
                "AuthRegistrationController.java"}) {
            String source = Files.readString(Path.of(
                    "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller", file));
            assertThat(source).contains("cookieWriter.writeAuthFlowCookie");
        }
    }
}
