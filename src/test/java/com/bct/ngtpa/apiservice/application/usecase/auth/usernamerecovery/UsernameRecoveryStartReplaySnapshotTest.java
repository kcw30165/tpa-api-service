package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoverySchemeProjection;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult.Outcome;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyRecord.ReplaySnapshot;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartReplaySnapshot.DecodeResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Task 019 safe non-session replay projection: {@code MEMBER_NOT_FOUND} and {@code
 * ZERO_REGISTERED} start results (the only approved safe start projections that never carry session
 * data or CSRF) round-trip exactly through the opaque idempotency {@link ReplaySnapshot}; {@code
 * SESSION_CREATED} is deliberately rejected (it belongs to the committed 019B {@code
 * UsernameRecoveryStartReplayCodec}), and corrupt snapshots are decoded as definite {@code
 * MALFORMED} (fail closed, never a blind second session).
 *
 * <p>Design references: {@code docs/design/6-username-recovery/handover.md} line 386 (replay
 * snapshots never contain username, raw destination, identity, DOB, CSRF, cookie, Scheme-Ref
 * secrets, or raw exception data) and {@code
 * docs/design/6-username-recovery/username-recovery-api-contract-design.md} section 8 (same-key
 * completed replay returns the original 200-shaped/result-shaped response).
 */
class UsernameRecoveryStartReplaySnapshotTest {

  @Test
  void memberNotFoundRoundTripsThroughReplaySnapshot() {
    UsernameRecoveryStartResult original =
        new UsernameRecoveryStartResult(Outcome.MEMBER_NOT_FOUND, null, null, List.of());

    ReplaySnapshot snapshot = UsernameRecoveryStartReplaySnapshot.encode(original);
    DecodeResult decoded = UsernameRecoveryStartReplaySnapshot.decode(snapshot);

    assertThat(decoded.decoded()).isTrue();
    assertThat(decoded.result()).isEqualTo(original);
  }

  @Test
  void zeroRegisteredRoundTripsWithUnregisteredProjections() {
    UsernameRecoveryStartResult original =
        new UsernameRecoveryStartResult(
            Outcome.ZERO_REGISTERED,
            null,
            null,
            List.of(
                new UsernameRecoverySchemeProjection(null, "DB", "DBA", false),
                new UsernameRecoverySchemeProjection(null, "DB", "DBB", false)));

    ReplaySnapshot snapshot = UsernameRecoveryStartReplaySnapshot.encode(original);
    DecodeResult decoded = UsernameRecoveryStartReplaySnapshot.decode(snapshot);

    assertThat(decoded.decoded()).isTrue();
    assertThat(decoded.result()).isEqualTo(original);
    assertThat(decoded.result().schemes())
        .extracting(UsernameRecoverySchemeProjection::registered)
        .containsOnly(false);
    assertThat(decoded.result().schemes())
        .allSatisfy(projection -> assertThat(projection.schemeRef()).isNull());
  }

  @Test
  void sessionCreatedIsRejectedByThisCodec() {
    UsernameRecoveryStartResult sessionCreated =
        new UsernameRecoveryStartResult(
            Outcome.SESSION_CREATED,
            "csrf-token",
            600,
            List.of(
                new UsernameRecoverySchemeProjection(
                    new com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef(
                        "sch_opaque-1"),
                    "DB",
                    "DBA",
                    true)));

    assertThatThrownBy(() -> UsernameRecoveryStartReplaySnapshot.encode(sessionCreated))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("UsernameRecoveryStartReplayCodec");
  }

  @Test
  void rejectsCorruptSnapshotsAsDefiniteMalformed() {
    assertThat(
            UsernameRecoveryStartReplaySnapshot.decode(new ReplaySnapshot("not-a-valid-encoding"))
                .decoded())
        .isFalse();
    assertThat(
            UsernameRecoveryStartReplaySnapshot.decode(
                    new ReplaySnapshot("ur-start-non-session:v1"))
                .decoded())
        .isFalse();
    assertThat(
            UsernameRecoveryStartReplaySnapshot.decode(
                    new ReplaySnapshot("ur-start-non-session:v1:zero-registered:"))
                .decoded())
        .isFalse();
    assertThat(
            UsernameRecoveryStartReplaySnapshot.decode(
                    new ReplaySnapshot("ur-start-non-session:v1:zero-registered:3:DB:0:"))
                .decoded())
        .isFalse();
  }

  @Test
  void rejectsNullInputsAsDefiniteMalformed() {
    assertThat(UsernameRecoveryStartReplaySnapshot.decode(null).decoded()).isFalse();
    assertThatThrownBy(() -> UsernameRecoveryStartReplaySnapshot.encode(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void snapshotValueNeverDisclosesRawResultData() {
    UsernameRecoveryStartResult original =
        new UsernameRecoveryStartResult(
            Outcome.ZERO_REGISTERED,
            null,
            null,
            List.of(new UsernameRecoverySchemeProjection(null, "DB", "DBA", false)));

    ReplaySnapshot snapshot = UsernameRecoveryStartReplaySnapshot.encode(original);

    assertThat(snapshot.toString()).doesNotContain(snapshot.value());
    assertThat(snapshot.value()).doesNotContain("csrf");
  }
}
