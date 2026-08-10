package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery;

/**
 * Public request body for {@code POST /api/v1/auth/username-recovery/start}.
 *
 * <p>Design references:
 * {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md}
 * section 4 (exactly six fields; no captcha answer) and
 * {@code docs/design/6-username-recovery/username-recovery-solution-design.md}
 * section 4 (FE public identity values {@code GPN}, {@code GPFS},
 * {@code STAFF_NO}, {@code ID}; {@code STAFF_NO} is mapped to
 * {@code MemberIdentityType.STAFFNO} by the web mapper).
 *
 * <p>Explicitly excluded from this body record: captcha answer, Idempotency-Key,
 * request ID, language, cookie, CSRF, Scheme-Ref, HTTP objects, and headers
 * (Task 022 line 49). The mapper receives language and idempotencyKey as
 * explicit method parameters. No business validation is performed here beyond
 * structural null/type safety.
 *
 * <p>{@link #toString()} never discloses identity, DOB, contact, or captcha
 * values.
 */
public record UsernameRecoveryStartRequest(
        String identityType,
        String identityValue,
        String dateOfBirth,
        String email,
        String mobile,
        String captchaChallengeId) {

    @Override
    public String toString() {
        return "UsernameRecoveryStartRequest[identityType=" + identityType
                + ", identityValue=<redacted>, dateOfBirth=<redacted>, email=<redacted>"
                + ", mobile=<redacted>, captchaChallengeId=<redacted>]";
    }
}
