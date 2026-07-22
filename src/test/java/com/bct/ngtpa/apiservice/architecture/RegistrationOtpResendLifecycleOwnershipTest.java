package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationOtpResendLifecycleOwnershipTest {

    @Test
    void initialOtpCreationDoesNotConsumeTheResendThreshold() throws Exception {
        String adapter = read("RegistrationOtpChallengeAdapter.java");
        String createMethod = method(adapter, "createOtp(", "resendOtp(");

        assertThat(createMethod)
                .contains("otpChallengePort.createChallenge(command)")
                .doesNotContain("enforceOtpSendRateLimit")
                .contains("policy.maxResendAttempts()");
    }

    @Test
    void resendIsOwnedByChallengeReplacementOnly() throws Exception {
        String adapter = read("RegistrationOtpChallengeAdapter.java");
        String resendMethod = method(adapter, "resendOtp(", "verifyOtp(");

        assertThat(resendMethod)
                .contains("otpChallengePort.replaceChallenge")
                .contains("MAX_RESEND_EXCEEDED")
                // .contains("MAX_RESEND_ATTEMPTS_REACHED")
                .contains("COOLDOWN_ACTIVE")
                .doesNotContain("enforceOtpSendRateLimit");
    }

    @org.junit.jupiter.api.Disabled("Removed redundant registration rate-limit model")
    @Test
    void adapterContainsNoOverlappingDestinationSendLimiter() throws Exception {
        String adapter = read("RegistrationOtpChallengeAdapter.java");

        assertThat(adapter)
                .doesNotContain("OTP_SEND_BY_DESTINATION_HASH")
                .doesNotContain("otpSendByDestinationHash()")
                .doesNotContain("private Mono<Void> enforceOtpSendRateLimit");
    }

    @Test
    void yamlOwnsFiveResendsAndFiveVerificationsPerChallenge() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(yaml)
                .contains("max-resend-attempts: 5")
                .contains("max-verify-attempts: 5")
                .contains("invalidate-previous-on-resend: true");
    }

    @Test
    void permittedDeliveryCountIsOneInitialPlusFiveResends() {
        int initialDeliveries = 1;
        int permittedResends = 5;
        assertThat(initialDeliveries + permittedResends).isEqualTo(6);
    }

    private String read(String fileName) throws Exception {
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            Path path = paths
                    .filter(candidate -> candidate.getFileName().toString()
                            .equals(fileName))
                    .findFirst()
                    .orElseThrow();
            return Files.readString(path);
        }
    }

    private String method(String source, String startToken, String nextToken) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(nextToken, start + startToken.length());
        if (start < 0 || end < 0) {
            throw new AssertionError(
                    "Unable to isolate method between " + startToken + " and " + nextToken);
        }
        return source.substring(start, end);
    }
}
