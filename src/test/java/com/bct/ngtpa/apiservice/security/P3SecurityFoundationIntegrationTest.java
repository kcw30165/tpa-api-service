package com.bct.ngtpa.apiservice.security;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.ConfigBackedAuthWorkflowPolicyAdapter;
import com.bct.ngtpa.apiservice.application.dto.security.csrf.CsrfToken;
import com.bct.ngtpa.apiservice.application.dto.security.ratelimit.RateLimitBucket;
import com.bct.ngtpa.apiservice.application.dto.security.ratelimit.RateLimitCommand;
import com.bct.ngtpa.apiservice.application.service.security.csrf.OriginRefererValidationStatus;
import com.bct.ngtpa.apiservice.application.service.security.csrf.OriginRefererValidator;
import com.bct.ngtpa.apiservice.application.service.security.csrf.SecureCsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.service.security.csrf.Sha256CsrfTokenHasher;
import com.bct.ngtpa.apiservice.application.service.security.ratelimit.DestinationType;
import com.bct.ngtpa.apiservice.application.service.security.ratelimit.Sha256DestinationBucketIdGenerator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class P3SecurityFoundationIntegrationTest {

    @Test
    void p3FoundationExposesConfiguredRegistrationRateLimits() {
        AuthWorkflowProperties properties = new AuthWorkflowProperties();
        AuthWorkflowProperties.Registration registration = new AuthWorkflowProperties.Registration();
        properties.setRegistration(registration);
        registration.getRateLimit().getRegistrationStart().getByIp().setEnabled(true);
        registration.getRateLimit().getRegistrationStart().getByIp().setMaxAttempts(10);
        registration.getRateLimit().getRegistrationStart().getByIp().setWindowSeconds(600);
        registration.getRateLimit().getOtpSend().getByDestinationHash().setEnabled(true);
        registration.getRateLimit().getOtpSend().getByDestinationHash().setMaxAttempts(3);
        registration.getRateLimit().getOtpSend().getByDestinationHash().setWindowSeconds(600);
        registration.getRateLimit().getOtpVerify().getByRegistrationSession().setEnabled(true);
        registration.getRateLimit().getOtpVerify().getByRegistrationSession().setMaxAttempts(5);
        registration.getRateLimit().getOtpVerify().getByRegistrationSession().setWindowSeconds(300);

        var policy = new ConfigBackedAuthWorkflowPolicyAdapter(properties).registrationPolicy().rateLimit();

        assertThat(policy.registrationStartByIp().enabled()).isTrue();
        assertThat(policy.registrationStartByIp().maxAttempts()).isEqualTo(10);
        assertThat(policy.registrationStartByIp().windowSeconds()).isEqualTo(600);
        assertThat(policy.otpSendByDestinationHash().enabled()).isTrue();
        assertThat(policy.otpSendByDestinationHash().maxAttempts()).isEqualTo(3);
        assertThat(policy.otpSendByDestinationHash().windowSeconds()).isEqualTo(600);
        assertThat(policy.otpVerifyByRegistrationSession().enabled()).isTrue();
        assertThat(policy.otpVerifyByRegistrationSession().maxAttempts()).isEqualTo(5);
        assertThat(policy.otpVerifyByRegistrationSession().windowSeconds()).isEqualTo(300);
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
    void p3CsrfTokenFoundationStoresHashNotRawToken() {
        String token = new SecureCsrfTokenGenerator().generateToken();
        Sha256CsrfTokenHasher hasher = new Sha256CsrfTokenHasher();
        String hash = hasher.hash(token);
        var persisted = new CsrfToken(
                "workflow_opaque",
                hash,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:10:00Z"));

        assertThat(token).isNotBlank().matches("^[A-Za-z0-9_.-]+$");
        assertThat(hash).isNotBlank().isNotEqualTo(token);
        assertThat(persisted.tokenHash()).isEqualTo(hash);
        Set<String> fields = Arrays.stream(CsrfToken.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
        assertThat(fields).doesNotContain("token", "rawToken", "csrfToken", "plainToken");
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
