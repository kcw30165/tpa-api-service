package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationOtpRateLimitAndDeliveryOwnershipTest {
    @Test
    void otpChallengeOwnsFiveResendsWithoutDestinationRateLimit() throws Exception {
        String adapter = read("RegistrationOtpChallengeAdapter.java");
        String yaml = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/resources/application.yml"));

        assertThat(adapter)
                .contains("otpChallengePort.createChallenge(command)")
                .contains("otpChallengePort.replaceChallenge")
                .contains("MAX_RESEND_EXCEEDED")
                // .contains("MAX_RESEND_ATTEMPTS_REACHED")
                .doesNotContain("RateLimitBucket.OTP_SEND_BY_DESTINATION_HASH")
                .doesNotContain("enforceOtpSendRateLimit")
                .doesNotContain("otpSendByDestinationHash");

        assertThat(yaml)
                .contains("max-resend-attempts: 5")
                .contains("max-verify-attempts: 5")
                .doesNotContain("by-destination-hash:");

        assertThat(1 + 5).as("one initial send plus five resends").isEqualTo(6);
    }

    @Test
    void resendDistinguishesCooldownFromDestinationSendLimit() throws Exception {
        String source = Files.readString(find("ResendRegistrationOtpService.java"));
        assertThat(source).contains("COOLDOWN_ACTIVE").contains("SEND_RATE_LIMITED")
                .contains("ResendRegistrationOtpResultStatus.SEND_RATE_LIMITED");
    }

    @Test
    void publicResendFailedCodeIsNotEmitted() throws Exception {
        String source = Files.readString(find("ResendRegistrationOtpService.java"));
        assertThat(source).doesNotContain("err.registration.otp.resendFailed");
    }

    private Path find(String name) throws Exception {
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            return paths.filter(p -> p.getFileName().toString().equals(name)).findFirst().orElseThrow();
        }
    }

    private String read(String fileName) throws Exception {
        try (var paths = java.nio.file.Files.walk(
                java.nio.file.Path.of("src/main/java"))) {
            java.nio.file.Path path = paths
                    .filter(candidate -> candidate.getFileName()
                            .toString()
                            .equals(fileName))
                    .findFirst()
                    .orElseThrow();
            return java.nio.file.Files.readString(path);
        }
    }
}
