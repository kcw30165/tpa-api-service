package com.bct.ngtpa.apiservice.adapter.out.security.usernamerecovery.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartProtectedReplayState;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmUsernameRecoveryStartReplayProtectorTest {
  private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);

  @Test
  void roundTripIsEncryptedAndBoundToReference() {
    var p = new AesGcmUsernameRecoveryStartReplayProtector("v1", SECRET);
    var ref = new ProtectedReplayReference("usernameRecoveryStartReplayReference001");
    var state =
        new UsernameRecoveryStartProtectedReplayState(
            new AuthFlowSessionId("afs_secret"), Instant.parse("2026-09-09T03:00:00Z"));
    var encrypted = p.protect(ref, state);
    assertThat(encrypted.ciphertext()).doesNotContain("afs_secret");
    assertThat(encrypted.toString()).doesNotContain(encrypted.iv(), encrypted.ciphertext());
    assertThat(p.unprotect(ref, encrypted)).isEqualTo(state);
    assertThatThrownBy(
            () ->
                p.unprotect(
                    new ProtectedReplayReference("usernameRecoveryStartReplayReference002"),
                    encrypted))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
