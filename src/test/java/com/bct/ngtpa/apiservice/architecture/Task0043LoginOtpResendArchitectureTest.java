package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class Task0043LoginOtpResendArchitectureTest {
    @Test
    void resendUsesSharedReplaceAndDeliveryCapabilities() throws Exception {
        String s = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/otp/resend/LoginOtpResendService.java"));
        assertThat(s).contains("replaceChallenge", "MessageDeliveryPort", "MessageDeliveryType.LOGIN_OTP");
        assertThat(s).doesNotContain("Redis", "WebClient", "org.springframework");
    }

    @Test
    void commandAndResultExcludeSecrets() throws Exception {
        String s = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/login/otp/resend/ResendLoginOtpResult.java"));
        assertThat(s).doesNotContain("csrf", "sessionId", "rawCode", "destinationId", "destination");
    }
}
