package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import java.util.Objects;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;

/**
 * Application-level lookup input for the Username Recovery APIM member search boundary.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-apim-integration-design.md}
 * section 2/5 (identity type and value map to {@code search-by}/{@code search-value}, DOB to
 * {@code dob}, and exactly one contact to {@code email} or {@code mobile-number}) and
 * {@code docs/design/6-username-recovery/username-recovery-solution-design.md} section 4 (shared
 * {@link MemberIdentityType}).
 *
 * <p>Invariants: {@code identityType}, {@code identityValue}, and {@code dateOfBirth} are required;
 * the string fields are trimmed and nonblank. Exactly-one email/mobile is a use-case validation rule
 * (UR-START-005) and is deliberately not enforced here. {@link #toString()} never discloses
 * identity, DOB, or contact values.
 */
public record UsernameRecoveryLookupCommand(
        MemberIdentityType identityType,
        String identityValue,
        String dateOfBirth,
        String email,
        String mobile) {

    public UsernameRecoveryLookupCommand {
        Objects.requireNonNull(identityType, "identityType must not be null");
        Objects.requireNonNull(identityValue, "identityValue must not be null");
        Objects.requireNonNull(dateOfBirth, "dateOfBirth must not be null");
        identityValue = identityValue.trim();
        dateOfBirth = dateOfBirth.trim();
        if (identityValue.isBlank() || dateOfBirth.isBlank()) {
            throw new IllegalArgumentException("username recovery lookup command fields must not be blank");
        }
    }

    @Override
    public String toString() {
        return "UsernameRecoveryLookupCommand[identityType=" + identityType
                + ", identityValue=<redacted>, dateOfBirth=<redacted>, email=<redacted>, mobile=<redacted>]";
    }
}
