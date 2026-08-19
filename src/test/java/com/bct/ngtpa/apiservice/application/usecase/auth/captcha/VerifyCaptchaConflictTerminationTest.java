package com.bct.ngtpa.apiservice.application.usecase.auth.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.*;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class VerifyCaptchaConflictTerminationTest {
  private static final Instant NOW = Instant.parse("2026-07-07T12:00:00Z");
  private static final CaptchaChallengeId ID = new CaptchaChallengeId("cap_abcdefghijklmnop");
  private static final CaptchaAnswerHash HASH = new CaptchaAnswerHash("0".repeat(64));

  @Test
  void persistentCreatedConflictReloadsOnceWithoutSecondMatchOrMutation() {
    FakePort port = new FakePort(created(), Optional.of(created()));
    CountingHasher hasher = new CountingHasher();
    var service =
        new VerifyCaptchaService(policy(), hasher, port, Clock.fixed(NOW, ZoneOffset.UTC));
    StepVerifier.create(service.verify(new VerifyCaptchaChallengeCommand(ID, "answer", "corr")))
        .assertNext(
            r -> {
              assertThat(r.verified()).isFalse();
              assertThat(r.outcome()).isEqualTo(CaptchaVerificationOutcome.INVALID);
            })
        .verifyComplete();
    assertThat(port.loadCalls).isEqualTo(2);
    assertThat(port.mutationCalls).isEqualTo(1);
    assertThat(hasher.calls).isEqualTo(1);
  }

  @Test
  void terminalWinnerReloadsOnceWithoutSecondMutation() {
    FakePort port = new FakePort(created(), Optional.of(state(CaptchaStatus.VERIFIED)));
    CountingHasher hasher = new CountingHasher();
    StepVerifier.create(
            new VerifyCaptchaService(policy(), hasher, port, Clock.fixed(NOW, ZoneOffset.UTC))
                .verify(new VerifyCaptchaChallengeCommand(ID, "answer", "corr")))
        .assertNext(r -> assertThat(r.verified()).isFalse())
        .verifyComplete();
    assertThat(port.loadCalls).isEqualTo(2);
    assertThat(port.mutationCalls).isEqualTo(1);
    assertThat(hasher.calls).isEqualTo(1);
  }

  @Test
  void missingWinnerReloadsOnceWithoutSecondMutation() {
    FakePort port = new FakePort(created(), Optional.empty());
    CountingHasher hasher = new CountingHasher();
    StepVerifier.create(
            new VerifyCaptchaService(policy(), hasher, port, Clock.fixed(NOW, ZoneOffset.UTC))
                .verify(new VerifyCaptchaChallengeCommand(ID, "answer", "corr")))
        .assertNext(r -> assertThat(r.outcome()).isEqualTo(CaptchaVerificationOutcome.NOT_FOUND))
        .verifyComplete();
    assertThat(port.loadCalls).isEqualTo(2);
    assertThat(port.mutationCalls).isEqualTo(1);
    assertThat(hasher.calls).isEqualTo(1);
  }

  private static AuthWorkflowPolicyPort policy() {
    return new AuthWorkflowPolicyPort() {
      public RegistrationAuthWorkflowPolicy registrationPolicy() {
        return new RegistrationAuthWorkflowPolicy(
            null, new CaptchaPolicy(8, "ABC123", false, 123, 5), null, null, null);
      }

      public Optional<String> resolveIdentityTypeSearchScope(String v) {
        return Optional.empty();
      }
    };
  }

  private static CaptchaChallenge created() {
    return state(CaptchaStatus.CREATED);
  }

  private static CaptchaChallenge state(CaptchaStatus s) {
    return new CaptchaChallenge(
        ID,
        s == CaptchaStatus.CONSUMED ? null : HASH,
        s,
        0,
        5,
        NOW.minusSeconds(60),
        NOW.plusSeconds(300),
        s == CaptchaStatus.VERIFIED ? NOW : null,
        s == CaptchaStatus.CONSUMED ? NOW : null,
        null,
        null);
  }

  private static final class CountingHasher implements CaptchaAnswerHasherPort {
    int calls;

    public CaptchaAnswerHash hash(String a, boolean c) {
      throw new UnsupportedOperationException();
    }

    public boolean matches(String a, CaptchaAnswerHash h, boolean c) {
      calls++;
      return true;
    }
  }

  private static final class FakePort implements CaptchaChallengePort {
    final CaptchaChallenge first;
    final Optional<CaptchaChallenge> reloaded;
    int loadCalls;
    int mutationCalls;

    FakePort(CaptchaChallenge first, Optional<CaptchaChallenge> reloaded) {
      this.first = first;
      this.reloaded = reloaded;
    }

    public Mono<Optional<CaptchaChallenge>> load(CaptchaChallengeId id) {
      loadCalls++;
      return Mono.just(loadCalls == 1 ? Optional.of(first) : reloaded);
    }

    public Mono<CaptchaVerificationMutationResult> applyVerificationAttempt(
        CaptchaChallenge e, boolean m, Instant at) {
      mutationCalls++;
      return Mono.just(
          CaptchaVerificationMutationResult.of(
              CaptchaVerificationMutationResult.Outcome.STATE_CHANGED, null));
    }

    public Mono<CaptchaChallenge> create(CaptchaChallenge c) {
      throw new UnsupportedOperationException();
    }

    public Mono<CaptchaChallenge> markVerified(CaptchaChallengeId i, Instant a) {
      throw new UnsupportedOperationException();
    }

    public Mono<CaptchaChallenge> incrementFailedAttempt(CaptchaChallengeId i, Instant a) {
      throw new UnsupportedOperationException();
    }

    public Mono<CaptchaChallenge> markLocked(CaptchaChallengeId i, Instant a) {
      throw new UnsupportedOperationException();
    }

    public Mono<CaptchaChallenge> markConsumed(CaptchaChallengeId i, Instant a) {
      throw new UnsupportedOperationException();
    }

    public Mono<CaptchaChallenge> markRefreshed(CaptchaChallengeId i, Instant a) {
      throw new UnsupportedOperationException();
    }

    public Mono<Void> delete(CaptchaChallengeId i) {
      return Mono.empty();
    }
  }
}
