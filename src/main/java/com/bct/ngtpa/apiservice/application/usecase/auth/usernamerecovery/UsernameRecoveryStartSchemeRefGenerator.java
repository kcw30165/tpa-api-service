package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Opaque Username Recovery Scheme-Ref generator (Task 019).
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-solution-design.md} section 6 step 11 (opaque
 * Scheme-Ref creation only when a session is created) and {@code
 * docs/design/6-username-recovery/username-recovery-test-traceability-design.md} section 6
 * (Scheme-Ref entropy at least 16 bytes; UR-R12 opaque and session-bound).
 *
 * <p>Values are base64url of 24 secure-random bytes with an opaque {@code sch_} prefix; the value
 * is never parsed for business meaning.
 */
public final class UsernameRecoveryStartSchemeRefGenerator {

  private static final int ENTROPY_BYTES = 24;
  private static final String PREFIX = "sch_";

  private final SecureRandom secureRandom = new SecureRandom();

  public UsernameRecoverySchemeRef generate() {
    byte[] bytes = new byte[ENTROPY_BYTES];
    secureRandom.nextBytes(bytes);
    return new UsernameRecoverySchemeRef(
        PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
  }
}
