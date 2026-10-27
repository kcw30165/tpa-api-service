package com.bct.ngtpa.apiservice.adapter.out.security.usernamerecovery;

import static org.assertj.core.api.Assertions.*;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.context.*;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.domain.model.*;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class AesGcmUsernameRecoveryAuthFlowContextProtectorAdapterTest {
  private static final String SECRET = "0123456789abcdef0123456789abcdef";
  private final AuthFlowSessionId id = new AuthFlowSessionId("afs_context-1");

  @Test
  void roundTripsRandomizedContextWithoutPlaintextLeakage() {
    var p = new AesGcmUsernameRecoveryAuthFlowContextProtectorAdapter("v1", SECRET);
    var first = p.protect(id, context());
    var second = p.protect(id, context());
    assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
    assertThat(p.unprotect(id, first)).isEqualTo(context());
    assertThat(first.ciphertext()).doesNotContain("member@example.com", "username-1", "sch_1");
    assertThat(first.toString()).doesNotContain(first.ciphertext());
  }

  @Test
  void rejectsTamperWrongKeyAndSessionRebinding() {
    var p = new AesGcmUsernameRecoveryAuthFlowContextProtectorAdapter("v1", SECRET);
    var encrypted = p.protect(id, context());
    String originalCiphertext = encrypted.ciphertext();
    char tamperedFirstChar = originalCiphertext.charAt(0) == 'A' ? 'B' : 'A';
    String tamperedCiphertext = tamperedFirstChar + originalCiphertext.substring(1);
    assertThat(tamperedCiphertext).isNotEqualTo(originalCiphertext);
    var tampered = encrypted.withCiphertext(tamperedCiphertext);
    assertThatThrownBy(() -> p.unprotect(id, tampered))
        .isInstanceOf(UsernameRecoveryAuthFlowContextProtectionException.class);
    var wrongKeyProtector =
        new AesGcmUsernameRecoveryAuthFlowContextProtectorAdapter(
            "v1", "abcdef0123456789abcdef0123456789");
    assertThatThrownBy(() -> wrongKeyProtector.unprotect(id, encrypted))
        .isInstanceOf(UsernameRecoveryAuthFlowContextProtectionException.class);
    var otherSessionId = new AuthFlowSessionId("afs_context-2");
    assertThatThrownBy(() -> p.unprotect(otherSessionId, encrypted))
        .isInstanceOf(UsernameRecoveryAuthFlowContextProtectionException.class);
  }

  private UsernameRecoveryAuthFlowContext context() {
    return new UsernameRecoveryAuthFlowContext(
        UsernameRecoveryWorkflowStatus.ACTIVE,
        new UsernameRecoveryVerifiedDestination(
            "member@example.com", UsernameRecoveryVerifiedDestination.Channel.EMAIL),
        Locale.ENGLISH,
        List.of(
            new UsernameRecoverySessionCandidate(
                new UsernameRecoveryCandidate(
                    new UsernameRecoverySchemeRef("sch_1"),
                    "DB",
                    "DBA",
                    true,
                    "username-1",
                    UsernameRecoveryDeliveryState.AVAILABLE,
                    null,
                    null),
                null,
                null)));
  }
}
