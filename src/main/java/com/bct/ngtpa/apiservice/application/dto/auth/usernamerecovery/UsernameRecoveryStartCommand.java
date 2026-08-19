package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import java.util.Locale;
import java.util.Objects;

/**
 * Validated application-level input for the Username Recovery start flow.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-api-contract-design.md} section 4 (start
 * request carries {@code identityType}, {@code identityValue}, {@code dateOfBirth}, {@code email},
 * {@code mobile}, and {@code captchaChallengeId}; there is no captcha answer; the {@code
 * Idempotency-Key} header is required) and {@code
 * docs/design/6-username-recovery/username-recovery-solution-design.md} section 4 (shared {@link
 * MemberIdentityType}; FE public {@code STAFF_NO} maps to {@code STAFFNO} at the web boundary).
 *
 * <p>Repair 018A (Option A): the normalized request language and the raw client-provided {@code
 * Idempotency-Key} enter through this typed command. {@code language} matches the committed {@code
 * UsernameRecoverySession} {@code java.util.Locale} field; it is required and never defaults.
 * {@code idempotencyKey} is the opaque client key, distinct from the semantic fingerprint; later
 * use cases may convert it into {@code UsernameRecoveryIdempotencyKey}. No raw CSRF is carried.
 *
 * <p>Invariants: {@code identityType}, {@code identityValue}, {@code dateOfBirth}, {@code
 * captchaChallengeId}, {@code language}, and {@code idempotencyKey} are required; the string fields
 * are trimmed and nonblank (the idempotency key keeps its case). Exactly-one email/mobile is a
 * use-case validation rule (UR-START-005) and is deliberately not enforced here. {@link
 * #toString()} never discloses identity, DOB, contact, captcha, or idempotency-key values.
 */
public record UsernameRecoveryStartCommand(
    MemberIdentityType identityType,
    String identityValue,
    String dateOfBirth,
    String email,
    String mobile,
    String captchaChallengeId,
    Locale language,
    String idempotencyKey) {

  public UsernameRecoveryStartCommand {
    Objects.requireNonNull(identityType, "identityType must not be null");
    Objects.requireNonNull(identityValue, "identityValue must not be null");
    Objects.requireNonNull(dateOfBirth, "dateOfBirth must not be null");
    Objects.requireNonNull(captchaChallengeId, "captchaChallengeId must not be null");
    Objects.requireNonNull(language, "language must not be null");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    identityValue = identityValue.trim();
    dateOfBirth = dateOfBirth.trim();
    captchaChallengeId = captchaChallengeId.trim();
    idempotencyKey = idempotencyKey.trim();
    if (identityValue.isBlank()
        || dateOfBirth.isBlank()
        || captchaChallengeId.isBlank()
        || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException(
          "username recovery start command fields must not be blank");
    }
  }

  @Override
  public String toString() {
    return "UsernameRecoveryStartCommand[identityType="
        + identityType
        + ", identityValue=, dateOfBirth=, email=, mobile="
        + ", captchaChallengeId=, language=, idempotencyKey=]";
  }
}
