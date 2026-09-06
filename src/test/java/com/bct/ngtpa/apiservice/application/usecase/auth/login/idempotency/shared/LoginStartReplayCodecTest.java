package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.login.replay.*;
import com.bct.ngtpa.apiservice.application.idempotency.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LoginStartReplayCodecTest {
  private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");
  private final RecordingReplayPort replay = new RecordingReplayPort();
  private Instant replayExpiresAt = NOW.plusSeconds(300);
  private final LoginStartReplayCodec codec =
      new LoginStartReplayCodec(
          new LoginSharedResponseEnvelope(testJson()),
          replay,
          protector(),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void capturesStartedWithoutProtectedValuesAndHydratesOriginalStatus() {
    StartLoginResult result = started();
    StepVerifier.create(codec.capture(result, transientResponse(result)))
        .assertNext(
            captured -> {
              assertThat(captured.snapshot().protectedReplayReference().value())
                  .isEqualTo("rpl_opaque_commit_reference");
              assertThat(captured.response().protectedReplayReference())
                  .isEqualTo(captured.snapshot().protectedReplayReference());
              assertThat(captured.snapshot().serializedSafeBody())
                  .contains("STARTED")
                  .doesNotContain("afs_secret", "csrf_secret", "dst_opaque", "m*****@example.com");
              StepVerifier.create(codec.hydrate(captured.snapshot()))
                  .assertNext(
                      hydrated -> {
                        assertThat(hydrated.status()).isEqualTo(StartLoginStatus.STARTED);
                        assertThat(hydrated.authFlowSessionId()).isEqualTo("afs_secret");
                        assertThat(hydrated.csrfToken()).isEqualTo("csrf_secret");
                      })
                  .verifyComplete();
            })
        .verifyComplete();
  }

  @Test
  void capturesDeterministicRejectionWithoutProtectedReference() {
    var result =
        new StartLoginResult(
            StartLoginStatus.INCORRECT_PASSWORD, null, null, null, List.of(), 2, null);
    StepVerifier.create(codec.capture(result, transientResponse(result)))
        .assertNext(
            captured -> {
              assertThat(captured.snapshot().protectedReplayReference()).isNull();
              StepVerifier.create(codec.hydrate(captured.snapshot()))
                  .assertNext(
                      hydrated ->
                          assertThat(hydrated.status())
                              .isEqualTo(StartLoginStatus.INCORRECT_PASSWORD))
                  .verifyComplete();
            })
        .verifyComplete();
    assertThat(replay.loads).isZero();
  }

  @Test
  void rejectsLifecycleStatusesAndInvalidReferencePresence() {
    assertThatThrownBy(
            () ->
                codec.capture(
                    new StartLoginResult(
                        StartLoginStatus.COMPLETED_REPLAY, null, null, null, List.of(), null, null),
                    transientResponse(
                        new StartLoginResult(
                            StartLoginStatus.COMPLETED_REPLAY,
                            null,
                            null,
                            null,
                            List.of(),
                            null,
                            null))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                codec.capture(
                    new StartLoginResult(
                        StartLoginStatus.STARTED, "afs", "csrf", 10, List.of(), 0, null),
                    transientResponse(
                        new StartLoginResult(
                            StartLoginStatus.STARTED, "afs", "csrf", 10, List.of(), 0, null))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                codec.capture(
                    new StartLoginResult(
                        StartLoginStatus.ACCOUNT_LOCKED,
                        null,
                        null,
                        null,
                        List.of(),
                        3,
                        "rpl_opaque_commit_reference"),
                    transientResponse(
                        new StartLoginResult(
                            StartLoginStatus.ACCOUNT_LOCKED,
                            null,
                            null,
                            null,
                            List.of(),
                            3,
                            "rpl_opaque_commit_reference"))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void failsClosedForWrongActionMissingOrExpiredProtectedReplay() {
    var body =
        new LoginSharedResponseEnvelope(testJson())
            .encode(
                LoginSharedIdempotencyAction.OTP_SEND,
                new LoginStartReplayCodec.SafePayload("STARTED", 0));
    var wrong =
        new IdempotentTerminalResponseSnapshot(
            1,
            200,
            "application/json",
            body,
            Map.of(),
            new ProtectedReplayReference("rpl_opaque_commit_reference"));
    assertThatThrownBy(() -> codec.hydrate(wrong))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Login replay action mismatch");
    replay.value = Optional.empty();
    StepVerifier.create(
            codec
                .capture(started(), transientResponse(started()))
                .flatMap(c -> codec.hydrate(c.snapshot())))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void rejectsUnsupportedSchemaAndInvalidMediaTypeBeforeDecoding() {
    var valid = codec.capture(started(), transientResponse(started())).block().snapshot();
    assertThatThrownBy(
            () ->
                new IdempotentTerminalResponseSnapshot(
                    2,
                    valid.httpStatus(),
                    valid.mediaType(),
                    valid.serializedSafeBody(),
                    valid.safeHeaders(),
                    valid.protectedReplayReference()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("unsupported idempotent response snapshot schema");
    assertThatThrownBy(
            () ->
                new IdempotentTerminalResponseSnapshot(
                    valid.schemaVersion(),
                    valid.httpStatus(),
                    "text/plain",
                    valid.serializedSafeBody(),
                    valid.safeHeaders(),
                    valid.protectedReplayReference()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("idempotent response media type is unsupported");

    assertThat(replay.loads).isZero();
  }

  @Test
  void failsClosedForActuallyExpiredProtectedReplay() {
    replayExpiresAt = NOW.minusSeconds(1);

    StepVerifier.create(
            codec
                .capture(started(), transientResponse(started()))
                .flatMap(captured -> codec.hydrate(captured.snapshot())))
        .expectError(IllegalArgumentException.class)
        .verify();

    assertThat(replay.loads).isEqualTo(1);
  }

  @Test
  void preservesTransportStatusMatrix() {
    assertThat(LoginStartReplayCodec.httpStatus(StartLoginStatus.STARTED)).isEqualTo(200);
    assertThat(LoginStartReplayCodec.httpStatus(StartLoginStatus.INCORRECT_PASSWORD))
        .isEqualTo(401);
    assertThat(LoginStartReplayCodec.httpStatus(StartLoginStatus.INCORRECT_USERNAME))
        .isEqualTo(401);
    assertThat(LoginStartReplayCodec.httpStatus(StartLoginStatus.INVALID_ACCOUNT)).isEqualTo(401);
    assertThat(LoginStartReplayCodec.httpStatus(StartLoginStatus.ACCOUNT_LOCKED)).isEqualTo(423);
  }

  private HydratedIdempotentResponse transientResponse(StartLoginResult result) {
    return new HydratedIdempotentResponse(
        LoginStartReplayCodec.httpStatus(result.status()),
        "application/json",
        "{\"transient\":true}",
        Map.of());
  }

  private StartLoginResult started() {
    return new StartLoginResult(
        StartLoginStatus.STARTED,
        "afs_secret",
        "csrf_secret",
        300,
        List.of(new LoginDestinationChoice("dst_opaque", "email", "m*****@example.com")),
        0,
        "rpl_opaque_commit_reference");
  }

  private LoginStartReplayProtectorPort protector() {
    return new LoginStartReplayProtectorPort() {
      public ProtectedLoginStartReplay protect(LoginStartReplayPayload value) {
        throw new AssertionError("capture must not protect again");
      }

      public LoginStartReplayPayload unprotect(ProtectedLoginStartReplay value) {
        return new LoginStartReplayPayload(
            "afs_secret",
            "csrf_secret",
            300,
            List.of(new LoginDestinationChoice("dst_opaque", "email", "m*****@example.com")),
            replayExpiresAt);
      }
    };
  }

  private static final class RecordingReplayPort implements LoginStartReplayPort {
    int loads;
    Optional<ProtectedLoginStartReplay> value =
        Optional.of(new ProtectedLoginStartReplay("v1", "AES/GCM/NoPadding", "iv", "cipher"));

    public Mono<Void> save(
        LoginStartReplayCommitRef ref, ProtectedLoginStartReplay replay, Duration ttl) {
      throw new AssertionError("capture must not store twice");
    }

    public Mono<Optional<ProtectedLoginStartReplay>> load(LoginStartReplayCommitRef ref) {
      loads++;
      return Mono.just(value);
    }

    public Mono<Boolean> delete(LoginStartReplayCommitRef ref) {
      throw new AssertionError("hydrate must not delete");
    }
  }

  private static com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort testJson() {
    return new com.bct.ngtpa.apiservice.adapter.out.json.JacksonTransientJsonAdapter(
        new com.fasterxml.jackson.databind.ObjectMapper());
  }
}
