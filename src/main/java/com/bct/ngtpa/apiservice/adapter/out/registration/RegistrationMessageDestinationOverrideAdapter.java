package com.bct.ngtpa.apiservice.adapter.out.registration;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationMessagePort;
import com.bct.ngtpa.apiservice.infrastructure.environment.DeploymentEnvironmentPolicy;
import java.util.Objects;
import reactor.core.publisher.Mono;

/**
 * Registration-specific delivery decorator that substitutes only the physical
 * destination outside production-like deployments.
 *
 * <p>
 * The original recipient remains owned by the registration workflow for
 * eligibility, masking, hashing, rate limiting, and session state. No
 * destination
 * value is logged or included in configuration errors.
 * </p>
 */
public class RegistrationMessageDestinationOverrideAdapter implements RegistrationMessagePort {

    private final RegistrationMessagePort delegate;
    private final DeploymentEnvironmentPolicy deploymentEnvironmentPolicy;
    private final AuthWorkflowProperties authWorkflowProperties;

    public RegistrationMessageDestinationOverrideAdapter(
            RegistrationMessagePort delegate,
            DeploymentEnvironmentPolicy deploymentEnvironmentPolicy,
            AuthWorkflowProperties authWorkflowProperties) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.deploymentEnvironmentPolicy = Objects.requireNonNull(
                deploymentEnvironmentPolicy,
                "deploymentEnvironmentPolicy must not be null");
        this.authWorkflowProperties = Objects.requireNonNull(
                authWorkflowProperties,
                "authWorkflowProperties must not be null");
    }

    @Override
    public Mono<Boolean> deliverRegistrationOtp(
            RegistrationContactType contactType,
            String recipient,
            String otpCode,
            int ttlSeconds,
            String acceptLanguage,
            String correlationId) {
        String effectiveRecipient = deploymentEnvironmentPolicy.isProductionLikeDeployment()
                ? recipient
                : nonProductionRecipient(contactType);
        return delegate.deliverRegistrationOtp(
                contactType,
                effectiveRecipient,
                otpCode,
                ttlSeconds,
                acceptLanguage,
                correlationId);
    }

    private String nonProductionRecipient(RegistrationContactType contactType) {
        Objects.requireNonNull(contactType, "contactType must not be null");
        AuthWorkflowProperties.DestinationOverride override = requiredDestinationOverride();
        override.validateRequiredDestinations();
        return switch (contactType) {
            case MOBILE -> override.requiredMobile();
            case EMAIL -> override.requiredEmail();
        };
    }

    private AuthWorkflowProperties.DestinationOverride requiredDestinationOverride() {
        AuthWorkflowProperties.Registration registration = authWorkflowProperties.getRegistration();
        if (registration == null) {
            throw new IllegalStateException("auth-workflows.registration configuration is required");
        }
        return registration.getMessage().getDestinationOverride();
    }
}
