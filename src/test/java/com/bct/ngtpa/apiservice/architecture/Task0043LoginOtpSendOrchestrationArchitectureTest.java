package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043LoginOtpSendOrchestrationArchitectureTest {
    @Test
    void orchestrationUsesSharedChallengeAndDeliveryPorts() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/otp/LoginOtpSendService.java"));
        assertThat(source).contains(
                "implements SendLoginOtpUseCase", "OtpChallengePort", "MessageDeliveryPort",
                "OtpScenario.LOGIN", "MessageDeliveryScenario.LOGIN", "MessageDeliveryType.LOGIN_OTP");
        assertThat(source).doesNotContain(
                "Redis", "WebClient", "org.springframework", "SecureRandom",
                "LoginOtpPort", "LoginMessagePort");
    }

    @Test
    void sensitiveCodeExistsOnlyBetweenChallengeCreationAndDelivery() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/otp/LoginOtpSendService.java"));
        assertThat(source).contains("challenge.rawCode()", "templateVariables");
        assertThat(source).doesNotContain("log.", "System.out", "rawCode=<", "providerMessageId");
    }

    @Test
    void protectedContextAndSessionBindingAreNarrowCapabilities() throws Exception {
        String loader = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/port/out/LoginProtectedAuthFlowContextLoader.java"));
        String binding = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/port/out/LoginOtpSessionBindingPort.java"));
        assertThat(loader + binding).doesNotContain(
                "AuthFlowSessionPort", "Redis", "Cipher", "org.springframework", "adapter.out");
    }
}
