package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

/**
 * Verified destination channel of a Username Recovery request.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md}
 * section 5 (request result {@code destinationType} such as {@code "EMAIL"}) and
 * {@code docs/design/6-username-recovery/username-recovery-solution-design.md} section 2 (exactly
 * one email/mobile input).
 */
public enum UsernameRecoveryDestinationType {
    EMAIL,
    MOBILE
}
