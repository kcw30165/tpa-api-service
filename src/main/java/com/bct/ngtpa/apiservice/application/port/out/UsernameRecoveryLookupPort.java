package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupResult;
import reactor.core.publisher.Mono;

/**
 * Outbound port for the Username Recovery member lookup against APIM
 * {@code TRPSearchMember}.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-apim-integration-design.md}
 * section 10 (the application port exposes domain outcomes, not APIM envelopes or DTOs) and
 * section 8 (adapter outcomes {@code MATCHED}, {@code NOT_FOUND}, {@code CONTRACT_INVALID},
 * {@code UNAVAILABLE}, and {@code UNKNOWN_OUTCOME}; downstream rejections never become public).
 *
 * <p>The port is reactive and exposes only application contracts. Implementations own APIM
 * transport, envelope validation, and safe outcome normalization. Only {@code X-Request-Id} is
 * propagated to APIM as request context.
 */
public interface UsernameRecoveryLookupPort {

    Mono<UsernameRecoveryLookupResult> search(UsernameRecoveryLookupCommand command);
}
