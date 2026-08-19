package com.bct.ngtpa.apiservice.adapter.out.redis.auth.revision;

import static org.assertj.core.api.Assertions.*;

import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AuthFlowSessionRevisionCreateLoadLegacyInitSubtask006001a2FailFirstTest {
  @Test
  void portExposesRevisionAwareFoundation() {
    assertThat(
            Arrays.stream(
                    com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort.class
                        .getMethods())
                .map(Method::getName))
        .contains("createRevisioned", "loadSnapshot", "initializeLegacyRevision");
  }

  @Test
  void legacyInitializationCommandIsRedactedAndRequiresLockProof() {
    var command =
        new InitializeLegacyAuthFlowSessionRevisionCommand(
            sample(), new SessionLockHandle(true, "lock-key", "lock-token"));
    assertThat(command.toString())
        .contains("lockProofPresent=true", "expectedLegacySessionPresent=true")
        .doesNotContain("lock-key", "lock-token", "afs_secret", "csrf_secret");
  }

  @Test
  void outcomesAreTypedAndSafe() {
    assertThat(LegacyAuthFlowSessionRevisionInitializationOutcome.values())
        .containsExactly(
            LegacyAuthFlowSessionRevisionInitializationOutcome.LEGACY_REVISION_INITIALIZED,
            LegacyAuthFlowSessionRevisionInitializationOutcome.SESSION_NOT_FOUND,
            LegacyAuthFlowSessionRevisionInitializationOutcome.REVISION_ALREADY_PRESENT,
            LegacyAuthFlowSessionRevisionInitializationOutcome.LEGACY_INITIALIZATION_CONFLICT,
            LegacyAuthFlowSessionRevisionInitializationOutcome.LOCK_OWNERSHIP_LOST,
            LegacyAuthFlowSessionRevisionInitializationOutcome.SCHEMA_NOT_ESTABLISHED,
            LegacyAuthFlowSessionRevisionInitializationOutcome.CORRUPT_STATE,
            LegacyAuthFlowSessionRevisionInitializationOutcome.SYSTEM_FAILURE);
  }

  private static AuthFlowSession sample() {
    return new AuthFlowSession(
        new AuthFlowSessionId("afs_secret"),
        AuthFlowScenario.LOGIN,
        AuthFlowStatus.OTP_SENT,
        "csrf_secret",
        "otp_secret",
        AuthFlowSessionData.empty(),
        java.time.Instant.parse("2026-08-01T00:00:00Z"),
        java.time.Instant.parse("2026-08-01T00:00:00Z"),
        java.time.Instant.parse("2026-08-01T00:30:00Z"));
  }
}
