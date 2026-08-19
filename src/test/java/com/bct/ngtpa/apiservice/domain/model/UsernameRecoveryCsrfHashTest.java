package com.bct.ngtpa.apiservice.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Verifies the protected CSRF hash value object.
 *
 * <p>Design references: {@code
 * docs/design/6-username-recovery/username-recovery-session-redis-design.md} section 3 (session
 * fields include the CSRF hash) and {@code
 * docs/design/6-username-recovery/username-recovery-solution-design.md} section 7 (CSRF hash
 * retained as minimal protected data). Task 010 invariants: CSRF storage retains a hash/protected
 * representation only, never the raw token.
 *
 * <p>Invariants: the hash is non-null and nonblank; {@link #toString()} never discloses the hash
 * value. Hashing algorithm and token generation belong to a later task, so only nonblank presence
 * is validated here.
 */
class UsernameRecoveryCsrfHashTest {

  @Test
  void retainsHashValue() {
    assertEquals("abc123", new UsernameRecoveryCsrfHash("abc123").value());
  }

  @Test
  void rejectsNull() {
    assertThrows(NullPointerException.class, () -> new UsernameRecoveryCsrfHash(null));
  }

  @Test
  void rejectsBlank() {
    assertThrows(IllegalArgumentException.class, () -> new UsernameRecoveryCsrfHash(""));
    assertThrows(IllegalArgumentException.class, () -> new UsernameRecoveryCsrfHash("   "));
  }

  @Test
  void toStringNeverDisclosesHashValue() {
    UsernameRecoveryCsrfHash hash = new UsernameRecoveryCsrfHash("secret-hash-value");
    assertEquals("UsernameRecoveryCsrfHash[value=<redacted>]", hash.toString());
  }
}
