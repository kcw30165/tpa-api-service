package com.bct.ngtpa.apiservice.domain.model;

/**
 * Opaque Username Recovery Scheme-Ref value object.
 *
 * <p>Design references:
 *
 * <ul>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-solution-design.md}
 *       section 6 step 11 (opaque Scheme-Ref creation) and section 11 (Scheme-Ref excluded
 *       from logs);</li>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-test-traceability-design.md}
 *       UR-R12 (Scheme-Ref opaque and session-bound);</li>
 *   <li>{@code docs/design/6-username-recovery/username-recovery-api-contract-design.md}
 *       section 4 (opaque schemeRef values such as {@code "sch_opaque-1"}).</li>
 * </ul>
 *
 * <p>Scheme-Ref is opaque and non-semantic: the value is never parsed for business meaning and
 * is never treated as an Account-Ref. Generation and entropy belong to a later task, so this
 * value object validates only that the opaque value is present and nonblank.
 * {@link #toString()} never discloses the raw value.
 */
public record UsernameRecoverySchemeRef(String value) {

    public UsernameRecoverySchemeRef {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("schemeRef value must not be null or blank");
        }
    }

    @Override
    public String toString() {
        return "UsernameRecoverySchemeRef[value=<redacted>]";
    }
}
