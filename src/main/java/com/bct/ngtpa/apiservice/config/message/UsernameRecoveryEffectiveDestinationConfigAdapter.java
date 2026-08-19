package com.bct.ngtpa.apiservice.config.message;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryEffectiveDestinationCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryEffectiveDestinationResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryEffectiveDestinationPort;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import com.bct.ngtpa.apiservice.infrastructure.environment.DeploymentEnvironmentPolicy;
import java.util.Objects;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Config-layer adapter resolving the effective Username Recovery delivery destination from the
 * committed deployment classification and dedicated Username Recovery override configuration.
 *
 * <p>PROD and DR return the verified destination unchanged. Every other deployment classification
 * uses the dedicated Username Recovery override for the matching channel and fails closed when the
 * applicable override is missing or blank. Login and Forgot Password overrides are never consulted.
 * The verified destination is never mutated, so it remains unchanged in the protected session.
 *
 * <p>Configuration failures disclose property names only, never configured values. Raw destination
 * values never appear in {@code toString()}, exception messages, or logs.
 */
@Component
public class UsernameRecoveryEffectiveDestinationConfigAdapter
    implements UsernameRecoveryEffectiveDestinationPort {

  private static final String USERNAME_RECOVERY_OVERRIDE_PREFIX =
      "auth-workflows.username-recovery.message.destination-override";

  private final DeploymentEnvironmentPolicy environmentPolicy;
  private final AuthWorkflowProperties properties;

  public UsernameRecoveryEffectiveDestinationConfigAdapter(
      DeploymentEnvironmentPolicy environmentPolicy, AuthWorkflowProperties properties) {
    this.environmentPolicy = Objects.requireNonNull(environmentPolicy);
    this.properties = Objects.requireNonNull(properties);
  }

  @Override
  public Mono<UsernameRecoveryEffectiveDestinationResult> resolve(
      UsernameRecoveryEffectiveDestinationCommand command) {
    return Mono.defer(
        () -> {
          Objects.requireNonNull(command, "command must not be null");
          UsernameRecoveryVerifiedDestination verified = command.verifiedDestination();
          if (environmentPolicy.isProductionLikeDeployment()) {
            return Mono.just(
                new UsernameRecoveryEffectiveDestinationResult(
                    verified.value(), verified.channel()));
          }
          String effectiveDestination =
              switch (verified.channel()) {
                case EMAIL -> requiredOverride().requiredEmail(USERNAME_RECOVERY_OVERRIDE_PREFIX);
                case MOBILE -> requiredOverride().requiredMobile(USERNAME_RECOVERY_OVERRIDE_PREFIX);
              };
          return Mono.just(
              new UsernameRecoveryEffectiveDestinationResult(
                  effectiveDestination, verified.channel()));
        });
  }

  private AuthWorkflowProperties.DestinationOverride requiredOverride() {
    if (properties.getUsernameRecovery() == null) {
      throw new IllegalStateException("auth-workflows.username-recovery configuration is required");
    }
    AuthWorkflowProperties.DestinationOverride override =
        properties.getUsernameRecovery().getMessage().getDestinationOverride();
    override.validateRequiredDestinations(USERNAME_RECOVERY_OVERRIDE_PREFIX);
    return override;
  }
}
