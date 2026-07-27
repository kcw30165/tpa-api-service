package com.bct.ngtpa.apiservice.adapter.out.registration;

import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestCase;
import com.bct.ngtpa.apiservice.integration.registration.support.RegistrationTestResultExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationMessagePort;
import com.bct.ngtpa.apiservice.infrastructure.environment.DeploymentEnvironmentPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(RegistrationTestResultExtension.class)
class RegistrationDeliveryEnvironmentIntegrationTest {

    @Test
    @RegistrationTestCase("TC-081")
    void nonProductionMobileDelivery_usesConfiguredMobileAndPreservesOtherArguments() {
        var delegate = new CapturingRegistrationMessagePort();
        var adapter = adapter("sit", configuredProperties(), delegate);

        StepVerifier.create(adapter.deliverRegistrationOtp(
                RegistrationContactType.MOBILE,
                "original-mobile",
                "123456",
                300,
                "en",
                "request-123"))
                .expectNext(true)
                .verifyComplete();

        assertThat(delegate.delivery).isEqualTo(new Delivery(
                RegistrationContactType.MOBILE,
                "override-mobile",
                "123456",
                300,
                "en",
                "request-123"));
    }

    @Test
    void nonProductionEmailDelivery_usesConfiguredEmail() {
        var delegate = new CapturingRegistrationMessagePort();
        var adapter = adapter("uat", configuredProperties(), delegate);

        StepVerifier.create(adapter.deliverRegistrationOtp(
                RegistrationContactType.EMAIL,
                "original@example.com",
                "654321",
                180,
                "zh-HK",
                "request-456"))
                .expectNext(true)
                .verifyComplete();

        assertThat(delegate.delivery.recipient()).isEqualTo("override@example.com");
        assertThat(delegate.delivery.contactType()).isEqualTo(RegistrationContactType.EMAIL);
    }

    @Test
    @RegistrationTestCase("TC-082")
    void productionLikeDelivery_preservesOriginalRecipientWithoutRequiringOverrides() {
        var properties = registrationProperties(null, null);
        var delegate = new CapturingRegistrationMessagePort();
        var adapter = adapter("prod", properties, delegate);

        StepVerifier.create(adapter.deliverRegistrationOtp(
                RegistrationContactType.MOBILE,
                "original-mobile",
                "123456",
                300,
                "en",
                "request-123"))
                .expectNext(true)
                .verifyComplete();

        assertThat(delegate.delivery.recipient()).isEqualTo("original-mobile");
    }

    @Test
    void disasterRecoveryDelivery_preservesOriginalRecipient() {
        var delegate = new CapturingRegistrationMessagePort();
        var adapter = adapter("DR", configuredProperties(), delegate);

        StepVerifier.create(adapter.deliverRegistrationOtp(
                RegistrationContactType.EMAIL,
                "original@example.com",
                "123456",
                300,
                "en",
                "request-123"))
                .expectNext(true)
                .verifyComplete();

        assertThat(delegate.delivery.recipient()).isEqualTo("original@example.com");
    }

    @Test
    void unknownEnvironment_isNonProductionAndUsesOverride() {
        var delegate = new CapturingRegistrationMessagePort();
        var adapter = adapter("future-env", configuredProperties(), delegate);

        StepVerifier.create(adapter.deliverRegistrationOtp(
                RegistrationContactType.MOBILE,
                "original-mobile",
                "123456",
                300,
                "en",
                "request-123"))
                .expectNext(true)
                .verifyComplete();

        assertThat(delegate.delivery.recipient()).isEqualTo("override-mobile");
    }

    @Test
    void missingEitherNonProductionDestination_rejectsDeliveryBeforeDelegateInvocation() {
        var delegate = new CapturingRegistrationMessagePort();
        var adapter = adapter("dev", registrationProperties("override-mobile", null), delegate);

        assertThatThrownBy(() -> adapter.deliverRegistrationOtp(
                RegistrationContactType.MOBILE,
                "original-mobile",
                "123456",
                300,
                "en",
                "request-123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email")
                .hasMessageNotContaining("override-mobile");
        assertThat(delegate.delivery).isNull();
    }

    @Test
    void nullContactTypeInNonProduction_rejectsDeliveryBeforeDelegateInvocation() {
        var delegate = new CapturingRegistrationMessagePort();
        var adapter = adapter("local", configuredProperties(), delegate);

        assertThatThrownBy(() -> adapter.deliverRegistrationOtp(
                null, "original", "123456", 300, "en", "request-123"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("contactType must not be null");
        assertThat(delegate.delivery).isNull();
    }

    private RegistrationMessageDestinationOverrideAdapter adapter(
            String profile,
            AuthWorkflowProperties properties,
            RegistrationMessagePort delegate) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return new RegistrationMessageDestinationOverrideAdapter(
                delegate,
                new DeploymentEnvironmentPolicy(environment),
                properties);
    }

    private AuthWorkflowProperties configuredProperties() {
        return registrationProperties(" override-mobile ", " override@example.com ");
    }

    private AuthWorkflowProperties registrationProperties(String mobile, String email) {
        var properties = new AuthWorkflowProperties();
        var registration = new AuthWorkflowProperties.Registration();
        registration.getMessage().getDestinationOverride().setMobile(mobile);
        registration.getMessage().getDestinationOverride().setEmail(email);
        properties.setRegistration(registration);
        return properties;
    }

    private static final class CapturingRegistrationMessagePort implements RegistrationMessagePort {
        private Delivery delivery;

        @Override
        public Mono<Boolean> deliverRegistrationOtp(
                RegistrationContactType contactType,
                String recipient,
                String otpCode,
                int ttlSeconds,
                String acceptLanguage,
                String correlationId) {
            delivery = new Delivery(
                    contactType, recipient, otpCode, ttlSeconds, acceptLanguage, correlationId);
            return Mono.just(true);
        }
    }

    private record Delivery(
            RegistrationContactType contactType,
            String recipient,
            String otpCode,
            int ttlSeconds,
            String acceptLanguage,
            String correlationId) {
    }
}
