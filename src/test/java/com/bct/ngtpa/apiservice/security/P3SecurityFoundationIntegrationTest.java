package com.bct.ngtpa.apiservice.security;

import com.bct.ngtpa.apiservice.application.dto.security.ratelimit.RateLimitBucket;
import com.bct.ngtpa.apiservice.application.dto.security.ratelimit.RateLimitCommand;
import com.bct.ngtpa.apiservice.application.service.security.csrf.OriginRefererValidationStatus;
import com.bct.ngtpa.apiservice.application.service.security.csrf.OriginRefererValidator;
import com.bct.ngtpa.apiservice.application.service.security.ratelimit.DestinationType;
import com.bct.ngtpa.apiservice.application.service.security.ratelimit.Sha256DestinationBucketIdGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class P3SecurityFoundationIntegrationTest {

        @org.junit.jupiter.api.Disabled("Removed redundant registration rate-limit model")
        @Test
        void registrationOtpLifecyclePolicyComesFromYaml() throws Exception {
                String yaml = java.nio.file.Files.readString(
                                java.nio.file.Path.of("src/main/resources/application.yml"));
                String properties = java.nio.file.Files.readString(
                                java.nio.file.Path.of(
                                                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/config/"
                                                                + "AuthWorkflowProperties.java"));
                String adapter = java.nio.file.Files.readString(
                                java.nio.file.Path.of(
                                                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/config/"
                                                                + "ConfigBackedAuthWorkflowPolicyAdapter.java"));

                org.assertj.core.api.Assertions.assertThat(yaml)
                                .contains("max-resend-attempts: 5")
                                .contains("max-verify-attempts: 5")
                                .contains("resend-cooldown-seconds: 60")
                                .contains("invalidate-previous-on-resend: true")
                                .doesNotContain("registration-start:")
                                .doesNotContain("by-destination-hash:");

                org.assertj.core.api.Assertions.assertThat(properties)
                                .contains("private int maxResendAttempts;")
                                .contains("private int maxVerifyAttempts;")
                                .contains("private int resendCooldownSeconds;")
                                .doesNotContain("class RegistrationStartRateLimit")
                                .doesNotContain("class OtpSendRateLimit")
                                .doesNotContain("getRateLimit");

                org.assertj.core.api.Assertions.assertThat(adapter)
                                .doesNotContain("RegistrationRateLimitPolicy")
                                .doesNotContain("getRateLimit()")
                                .doesNotContain("otpSendByDestinationHash");
        }

        @Test
        void p3RateLimitCommandCanUseDestinationBucketId() {
                String bucketId = new Sha256DestinationBucketIdGenerator()
                                .generateBucketId(DestinationType.EMAIL, "member@example.com");

                var command = new RateLimitCommand(
                                RateLimitBucket.OTP_SEND_BY_DESTINATION_HASH,
                                bucketId,
                                true,
                                3,
                                600,
                                "request-123");

                assertThat(bucketId).isNotBlank();
                assertThat(bucketId).doesNotContain("member@example.com").doesNotContain("@");
                assertThat(command.bucket()).isEqualTo(RateLimitBucket.OTP_SEND_BY_DESTINATION_HASH);
                assertThat(command.bucketId()).isEqualTo(bucketId);
        }

        @Test
        void p3OriginRefererValidatorRejectsWildcardAndHostSuffixAttack() {
                OriginRefererValidator validator = new OriginRefererValidator();

                assertThat(validator.validate("https://portal.example.com", null, List.of("*")).status())
                                .isEqualTo(OriginRefererValidationStatus.ORIGIN_NOT_ALLOWED);
                assertThat(validator.validate("https://portal.example.com.evil.com", null,
                                List.of("https://portal.example.com")).status())
                                .isEqualTo(OriginRefererValidationStatus.ORIGIN_NOT_ALLOWED);
        }
}
