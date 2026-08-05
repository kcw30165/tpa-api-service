package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class Task0043LoginOtpVerificationArchitectureTest {
        @Test
        void verificationUsesDedicatedAtomicBoundaryAndCancelsExhaustedFlow() throws Exception {
                String s = Files.readString(Path.of(
                                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/otp/verify/LoginOtpVerifyService.java"));
                assertThat(s)
                                .contains("LoginOtpVerifyPersistencePort", "AuthFlowSessionMutationLockPort",
                                                "AuthFlowSessionCancellationPort", "SECURITY_REJECTION",
                                                "LoginOtpVerifiedSessionPort", "case VERIFIED_AND_CONSUMED ->")
                                .doesNotContain(".verifyChallenge(new VerifyOtpChallengeCommand")
                                .doesNotContain("Redis", "WebClient", "LoginOtpPort");
        }

        @Test
        void verificationDtoTreeIsPresentAndScopedToVerify() throws Exception {
                Path otpRoot = Path.of(
                                "src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/login/otp");
                try (var entries = Files.list(otpRoot)) {
                        assertThat(entries.map(path -> path.getFileName().toString()).toList())
                                        .containsExactly("verify");
                }
        }
}
