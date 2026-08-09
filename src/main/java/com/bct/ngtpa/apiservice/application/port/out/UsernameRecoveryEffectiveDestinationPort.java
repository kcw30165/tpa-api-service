package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryEffectiveDestinationCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryEffectiveDestinationResult;
import reactor.core.publisher.Mono;

/**
 * Outbound port for resolving the effective Username Recovery delivery destination.
 *
 * <p>Design references:
 * {@code docs/design/6-username-recovery/username-recovery-nonprod-destination-override-design.md}
 * section 3 (production-like deployments deliver to the verified member destination; every other
 * deployment applies the dedicated Username Recovery override) and section 8 (missing or blank
 * applicable override must fail closed).
 *
 * <p>The port is reactive and exposes only application contracts. Implementations own the
 * deployment classification and dedicated override configuration; the request use case remains
 * free of environment-specific destination logic and uses the resolved effective destination only
 * to build the masked destination for FE.
 */
public interface UsernameRecoveryEffectiveDestinationPort {

    Mono<UsernameRecoveryEffectiveDestinationResult> resolve(
            UsernameRecoveryEffectiveDestinationCommand command);
}
