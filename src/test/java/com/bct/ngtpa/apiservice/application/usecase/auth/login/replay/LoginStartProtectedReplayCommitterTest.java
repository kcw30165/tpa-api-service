package com.bct.ngtpa.apiservice.application.usecase.auth.login.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.login.replay.LoginStartReplayCommitRef;
import com.bct.ngtpa.apiservice.application.dto.auth.login.replay.LoginStartReplayPayload;
import com.bct.ngtpa.apiservice.application.dto.auth.login.replay.ProtectedLoginStartReplay;
import com.bct.ngtpa.apiservice.application.port.out.LoginStartReplayCommitRefGenerator;
import com.bct.ngtpa.apiservice.application.port.out.LoginStartReplayPort;
import com.bct.ngtpa.apiservice.application.port.out.LoginStartReplayProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.LoginDestinationChoice;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.StartLoginResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.StartLoginStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LoginStartProtectedReplayCommitterTest {
  private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");

  @Test
  void protectsAndSavesExactlyOnceWithLowerMaximumTtl() {
    Fixture fixture = new Fixture();
    StepVerifier.create(fixture.committer.commit(started(900), Duration.ofSeconds(300)))
        .assertNext(
            value -> {
              assertThat(value.status()).isEqualTo(StartLoginStatus.STARTED);
              assertThat(value.authFlowSessionId()).isEqualTo("afs_secret");
              assertThat(value.csrfToken()).isEqualTo("csrf_secret");
              assertThat(value.expiresInSeconds()).isEqualTo(900);
              assertThat(value.destinations())
                  .containsExactlyElementsOf(started(900).destinations());
              assertThat(value.failCount()).isZero();
              assertThat(value.replayCommitRef()).isEqualTo(fixture.ref.value());
            })
        .verifyComplete();

    var payload = ArgumentCaptor.forClass(LoginStartReplayPayload.class);
    verify(fixture.protector).protect(payload.capture());
    assertThat(payload.getValue().expiresAt()).isEqualTo(NOW.plusSeconds(900));
    verify(fixture.storage).save(fixture.ref, fixture.encrypted, Duration.ofSeconds(300));
    var ordered = inOrder(fixture.refs, fixture.protector, fixture.storage);
    ordered.verify(fixture.refs).generate();
    ordered.verify(fixture.protector).protect(any());
    ordered.verify(fixture.storage).save(any(), any(), any());
  }

  @Test
  void usesShorterSessionLifetime() {
    Fixture fixture = new Fixture();
    StepVerifier.create(fixture.committer.commit(started(120), Duration.ofSeconds(300)))
        .expectNextCount(1)
        .verifyComplete();
    verify(fixture.storage).save(fixture.ref, fixture.encrypted, Duration.ofSeconds(120));
  }

  @Test
  void rejectsNullZeroAndNegativeSessionTtlBeforeOwnedWork() {
    assertInvalidTtl(started(null), Duration.ofSeconds(300));
    assertInvalidTtl(started(0), Duration.ofSeconds(300));
    assertInvalidTtl(started(-1), Duration.ofSeconds(300));
  }

  @Test
  void rejectsNullZeroAndNegativeMaximumTtlBeforeOwnedWork() {
    assertInvalidTtl(started(120), null);
    assertInvalidTtl(started(120), Duration.ZERO);
    assertInvalidTtl(started(120), Duration.ofSeconds(-1));
  }

  @Test
  void rejectsNonStartedBeforeOwnedWork() {
    Fixture fixture = new Fixture();
    var rejected =
        new StartLoginResult(
            StartLoginStatus.INCORRECT_PASSWORD, null, null, null, List.of(), 1, null);
    StepVerifier.create(fixture.committer.commit(rejected, Duration.ofSeconds(300)))
        .expectError(IllegalArgumentException.class)
        .verify();
    verifyNoInteractions(fixture.refs, fixture.protector, fixture.storage);
  }

  @Test
  void referenceGenerationFailurePropagatesWithoutProtectionOrSave() {
    Fixture fixture = new Fixture();
    when(fixture.refs.generate()).thenThrow(new IllegalStateException("reference failure"));
    StepVerifier.create(fixture.committer.commit(started(120), Duration.ofSeconds(300)))
        .expectErrorMessage("reference failure")
        .verify();
    verifyNoInteractions(fixture.protector, fixture.storage);
  }

  @Test
  void protectionFailurePropagatesWithoutSave() {
    Fixture fixture = new Fixture();
    when(fixture.protector.protect(any()))
        .thenThrow(new IllegalStateException("protection failure"));
    StepVerifier.create(fixture.committer.commit(started(120), Duration.ofSeconds(300)))
        .expectErrorMessage("protection failure")
        .verify();
    verify(fixture.refs).generate();
    verify(fixture.storage, never()).save(any(), any(), any());
  }

  @Test
  void saveFailurePropagates() {
    Fixture fixture = new Fixture();
    when(fixture.storage.save(any(), any(), any()))
        .thenReturn(Mono.error(new IllegalStateException("save failure")));
    StepVerifier.create(fixture.committer.commit(started(120), Duration.ofSeconds(300)))
        .expectErrorMessage("save failure")
        .verify();
    verify(fixture.storage).save(fixture.ref, fixture.encrypted, Duration.ofSeconds(120));
  }

  private void assertInvalidTtl(StartLoginResult result, Duration maximumTtl) {
    Fixture fixture = new Fixture();
    StepVerifier.create(fixture.committer.commit(result, maximumTtl))
        .expectError(IllegalArgumentException.class)
        .verify();
    verifyNoInteractions(fixture.refs, fixture.protector, fixture.storage);
  }

  private static StartLoginResult started(Integer expiresInSeconds) {
    return new StartLoginResult(
        StartLoginStatus.STARTED,
        "afs_secret",
        "csrf_secret",
        expiresInSeconds,
        List.of(new LoginDestinationChoice("dst_opaque", "email", "m*****@example.com")),
        0,
        null);
  }

  private static final class Fixture {
    private final LoginStartReplayPort storage = mock(LoginStartReplayPort.class);
    private final LoginStartReplayProtectorPort protector =
        mock(LoginStartReplayProtectorPort.class);
    private final LoginStartReplayCommitRefGenerator refs =
        mock(LoginStartReplayCommitRefGenerator.class);
    private final LoginStartReplayCommitRef ref =
        new LoginStartReplayCommitRef("rpl_opaque_commit_reference");
    private final ProtectedLoginStartReplay encrypted =
        new ProtectedLoginStartReplay("v1", "test", "iv", "cipher");
    private final LoginStartProtectedReplayCommitter committer;

    private Fixture() {
      when(refs.generate()).thenReturn(ref);
      when(protector.protect(any())).thenReturn(encrypted);
      when(storage.save(any(), any(), any())).thenReturn(Mono.empty());
      committer =
          new LoginStartProtectedReplayCommitter(
              storage, protector, refs, Clock.fixed(NOW, ZoneOffset.UTC));
    }
  }
}
