package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ForgotPasswordOtpReactiveArchitectureGuardTest {

    @Test
    void resendAndVerifyRemainReactiveAndAvoidCompletionLock() throws Exception {
        String resend = source(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/otp/resend/ResendForgotPasswordOtpService.java");
        String verify = source(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/otp/verify/VerifyForgotPasswordOtpService.java");

        assertThat(resend).contains("Mono.usingWhen(");
        assertThat(verify).contains("Mono.usingWhen(");
        assertThat(resend).doesNotContain(".subscribe(", ".block(", ".blockOptional(", "toFuture().get(",
                "AuthFlowSessionCompletionLockPort");
        assertThat(verify).doesNotContain(".subscribe(", ".block(", ".blockOptional(", "toFuture().get(",
                "AuthFlowSessionCompletionLockPort");
    }

    @Test
    void resendAndVerifyReloadRevisionedSnapshotsAndAcquireOneMutationLock() throws Exception {
        String resend = source(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/otp/resend/ResendForgotPasswordOtpService.java");
        String verify = source(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/otp/verify/VerifyForgotPasswordOtpService.java");

        assertThat(resend).contains("loadSnapshot(");
        assertThat(verify).contains("loadSnapshot(");
        assertThat(count(resend, "locks.acquire(")).isEqualTo(1);
        assertThat(count(verify, "locks.acquire(")).isEqualTo(1);
    }

    private static String source(String relativePath) throws Exception {
        return Files.readString(Path.of(System.getProperty("user.dir")).resolve(relativePath).normalize());
    }

    private static int count(String source, String token) {
        int total = 0;
        int index = 0;
        while ((index = source.indexOf(token, index)) >= 0) {
            total++;
            index += token.length();
        }
        return total;
    }
}