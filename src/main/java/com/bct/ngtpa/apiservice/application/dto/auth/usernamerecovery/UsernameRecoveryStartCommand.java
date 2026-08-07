package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import java.util.Objects;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;

/**
 * Validated application-level input for the Username Recovery start flow.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md}
 * section 4 (start request carries {@code identityType}, {@code identityValue}, {@code dateOfBirth},
 * {@code email}, {@code mobile}, and {@code captchaChallengeId}; there is no captcha answer) and
 * {@code docs/design/6-username-recovery/username-recovery-solution-design.md} section 4 (shared
 * {@link MemberIdentityType}; FE public {@code STAFF_NO} maps to {@code STAFFNO} at the web boundary).
 *
 * <p>Invariants: {@code identityType}, {@code identityValue}, {@code dateOfBirth}, and
 * {@code captchaChallengeId} are required; the string fields are trimmed and nonblank. Exactly-one
 * email/mobile is a use-case validation rule (UR-START-005) and is deliberately not enforced here.
 * {@link #toString()} never discloses identity, DOB, contact, or captcha values.
 */
public record UsernameRecoveryStartCommand(
        MemberIdentityType identityType,
        String identityValue,
        String dateOfBirth,
        String email,
        String mobile,
        String captchaChallengeId) {

    public UsernameRecoveryStartCommand {
        Objects.requireNonNull(identityType, "identityType must not be null");
        Objects.requireNonNull(identityValue, "identityValue must not be null");
        Objects.requireNonNull(dateOfBirth, "dateOfBirth must not be null");
        Objects.requireNonNull(captchaChallengeId, "captchaChallengeId must not be null");
        identityValue = identityValue.trim();
        dateOfBirth = dateOfBirth.trim();
        captchaChallengeId = captchaChallengeId.trim();
        if (identityValue.isBlank() || dateOfBirth.isBlank() || captchaChallengeId.isBlank()) {
            throw new IllegalArgumentException("username recovery start command fields must not be blank");
        }
    }

    @Override
    public String toString() {
        return "UsernameRecoveryStartCommand[identityType=" + identityType
                + ", identityValue=<redacted>, dateOfBirth=<redacted>, email=<redacted>, mobile=<redacted>, captchaChallengeId=<redacted>]";
    }
}
