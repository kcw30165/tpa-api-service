package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043LoginOtpSendContractTest {
    @Test
    void loginAddsScenariosToExistingSharedOtpAndMessageVocabularies() throws Exception {
        String otp = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/otp/OtpScenario.java"));
        String delivery = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/dto/message/MessageDeliveryScenario.java"));
        String type = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/dto/message/MessageDeliveryType.java"));
        assertThat(otp).contains("REGISTRATION", "LOGIN");
        assertThat(delivery).contains("REGISTRATION", "LOGIN");
        assertThat(type).contains("REGISTRATION_OTP", "LOGIN_OTP");
    }

    @Test
    void loginOtpContractReusesSharedTechnicalPortsAndIntroducesNoParallelStack() throws Exception {
        String root = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/port/out/OtpChallengePort.java"));
        String message = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/port/out/MessageDeliveryPort.java"));
        String input = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/port/in/SendLoginOtpUseCase.java"));
        assertThat(root).contains("AuthFlowOtpPort");
        assertThat(message).contains("AuthFlowMessagePort");
        assertThat(input).doesNotContain("OtpChallengePort", "MessageDeliveryPort");
        assertThat(Files.exists(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/port/out/LoginOtpPort.java"))).isFalse();
        assertThat(Files.exists(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/port/out/LoginMessagePort.java"))).isFalse();
    }

    @Test
    void inputContractContainsRequiredSecurityAndIdempotencyInputsButNoAccountRef() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/otp/SendLoginOtpCommand.java"));
        assertThat(source).contains(
                "authFlowSessionId", "csrfToken", "destinationId", "idempotencyKey",
                "origin", "referer", "acceptLanguage", "correlationId");
        assertThat(source).doesNotContain("accountRef", "AccountRef", "password", "otpCode");
    }

    @Test
    void sendResultNeverContainsCsrfSessionRawDestinationOrOtpCode() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/otp/SendLoginOtpResult.java"));
        assertThat(source).contains(
                "status", "maskedDestination", "expiresInSeconds", "resendCooldownSeconds");
        assertThat(source).doesNotContain(
                "csrfToken", "authFlowSessionId", "destinationId", "rawCode",
                "otpCode", "destination", "accountRef");
    }

    @Test
    void applicationContractHasNoSpringWebRedisOrAdapterDependency() throws Exception {
        String command = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/otp/SendLoginOtpCommand.java"));
        String result = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/otp/SendLoginOtpResult.java"));
        String resolver = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/otp/LoginOtpDestinationResolver.java"));
        assertThat(command + result + resolver).doesNotContain(
                "org.springframework", "ServerHttp", "WebClient", "Redis", "adapter.in", "adapter.out");
    }
}
