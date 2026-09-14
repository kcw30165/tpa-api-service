package com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.otp.verify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.RedisLoginIdempotencyKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.LoginIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.LoginIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.LoginIdempotencyPersistenceId;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.AuthenticatedLoginHandoffReference;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpConsumptionProof;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpVerificationOperationReference;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpVerifyPersistenceOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpVerifyReservation;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpVerifyReservationOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpVerifyReservationStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.RecordLoginOtpVerifyCommittedCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.RedactedSubmittedOtp;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.ReserveLoginOtpVerifyCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.RevisionedLoginOtpVerifyCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChallenge;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChannel;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpScenario;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpStatus;
import com.bct.ngtpa.apiservice.application.dto.session.SessionLockHandle;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengeStatePort;
import com.bct.ngtpa.apiservice.application.service.auth.login.idempotency.LoginIdempotencyPersistenceIdDeriver;
import com.bct.ngtpa.apiservice.application.service.auth.otp.OtpCodeHasher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented reactive tests for {@link RedisLoginOtpVerifyPersistenceAdapter}. Mirrors the
 * proven {@code ReactiveStringRedisTemplate}/{@code ReactiveValueOperations} mocking pattern used
 * elsewhere in this codebase (e.g. {@code RedisRateLimitAdapterTest}). Note: the adapter's {@code
 * mutate(...)} uses {@code Instant.now()} directly (no injectable clock), so success-path fixtures
 * use real-wall-clock-relative expiry instants.
 */
@SuppressWarnings("unchecked")
class RedisLoginOtpVerifyPersistenceAdapterTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private final OtpChallengeStatePort challenges = mock(OtpChallengeStatePort.class);
  private final OtpCodeHasher hasher = mock(OtpCodeHasher.class);
  private final RedisLoginIdempotencyKeyFactory keys = mock(RedisLoginIdempotencyKeyFactory.class);
  private final LoginIdempotencyPersistenceIdDeriver ids =
      mock(LoginIdempotencyPersistenceIdDeriver.class);
  private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
  private final ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);

  private final LoginIdempotencyKey publicKey = new LoginIdempotencyKey("public-key-abc12345");
  private final LoginIdempotencyPersistenceId persistenceId =
      new LoginIdempotencyPersistenceId("a".repeat(43));
  private final AuthenticatedLoginHandoffReference handoff =
      new AuthenticatedLoginHandoffReference("a".repeat(32));
  private final LoginOtpVerificationOperationReference operation =
      new LoginOtpVerificationOperationReference("b".repeat(32));

  private RedisLoginOtpVerifyPersistenceAdapter newAdapter() {
    when(redis.opsForValue()).thenReturn(values);
    when(ids.derive(any(), any())).thenReturn(persistenceId);
    when(keys.keyFor(any(), any())).thenReturn("redis-key-1");
    return new RedisLoginOtpVerifyPersistenceAdapter(challenges, hasher, keys, ids, redis);
  }

  private static String json(LoginOtpVerifyReservation r) {
    try {
      return MAPPER.writeValueAsString(r);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private LoginOtpVerifyReservation reservation(
      LoginOtpVerifyReservationStatus status,
      LoginOtpConsumptionProof proof,
      Instant createdAt,
      Instant expiresAt) {
    return new LoginOtpVerifyReservation(
        "req-hash-1", status, handoff, operation, proof, null, null, createdAt, expiresAt);
  }

  private OtpChallenge challenge(OtpStatus status, Instant expiresAt, int failed, int maxAttempts) {
    return new OtpChallenge(
        "challenge-1", OtpScenario.LOGIN, "afs-1", OtpChannel.EMAIL, "destinationhash",
        "codehash", status, failed, maxAttempts, 0, 5, Instant.now().minusSeconds(120),
        expiresAt, Instant.now().minusSeconds(60), null, null);
  }

  private RevisionedLoginOtpVerifyCommand verifyCommand(String challengeId, Instant verificationTime) {
    AuthFlowSessionId sid = new AuthFlowSessionId("afs-1");
    AuthFlowSession session =
        new AuthFlowSession(
            sid, AuthFlowScenario.LOGIN, AuthFlowStatus.OTP_SENT, "csrfhash", null,
            new AuthFlowSessionData("email", "j***@example.com", Map.of()),
            Instant.now().minusSeconds(300), Instant.now().minusSeconds(60),
            Instant.now().plusSeconds(1800));
    AuthFlowSessionSnapshot snapshot =
        new AuthFlowSessionSnapshot(session, new SessionRevision("0".repeat(32)));
    return new RevisionedLoginOtpVerifyCommand(
        new SessionLockHandle(true, "lock-key", "lock-token"),
        snapshot,
        challengeId,
        "reservation-hash-1",
        handoff,
        operation,
        new RedactedSubmittedOtp("123456"),
        verificationTime,
        new SessionRevision("1".repeat(32)));
  }

  // ----- reserveOrLoad ---------------------------------------------------------

  @Test
  void reserveOrLoadNewCreatesStarted() {
    when(values.setIfAbsent(any(), any(), any())).thenReturn(Mono.just(true));
    ReserveLoginOtpVerifyCommand cmd =
        new ReserveLoginOtpVerifyCommand(
            publicKey, "req-hash-1", handoff, operation, Instant.now(), Instant.now().plusSeconds(600));
    StepVerifier.create(newAdapter().reserveOrLoad(cmd))
        .assertNext(
            r -> {
              assertThat(r.outcome()).isEqualTo(LoginOtpVerifyReservationOutcome.STARTED);
              assertThat(r.reservation().status())
                  .isEqualTo(LoginOtpVerifyReservationStatus.PROCESSING);
            })
        .verifyComplete();
  }

  @Test
  void reserveOrLoadExistingDelegatesToLoadReservation() {
    when(values.setIfAbsent(any(), any(), any())).thenReturn(Mono.just(false));
    when(values.get(any()))
        .thenReturn(
            Mono.just(
                json(
                    reservation(
                        LoginOtpVerifyReservationStatus.PROCESSING, null,
                        Instant.now().minusSeconds(10), Instant.now().plusSeconds(600)))));
    ReserveLoginOtpVerifyCommand cmd =
        new ReserveLoginOtpVerifyCommand(
            publicKey, "req-hash-1", handoff, operation, Instant.now(), Instant.now().plusSeconds(600));
    StepVerifier.create(newAdapter().reserveOrLoad(cmd))
        .assertNext(r -> assertThat(r.outcome()).isEqualTo(LoginOtpVerifyReservationOutcome.EXISTING))
        .verifyComplete();
  }

  // ----- loadReservation ---------------------------------------------------------

  @Test
  void loadReservationHashMatchYieldsExisting() {
    when(values.get(any()))
        .thenReturn(
            Mono.just(
                json(
                    reservation(
                        LoginOtpVerifyReservationStatus.PROCESSING, null,
                        Instant.now().minusSeconds(10), Instant.now().plusSeconds(600)))));
    StepVerifier.create(newAdapter().loadReservation(publicKey, "req-hash-1"))
        .assertNext(r -> assertThat(r.outcome()).isEqualTo(LoginOtpVerifyReservationOutcome.EXISTING))
        .verifyComplete();
  }

  @Test
  void loadReservationHashMismatchYieldsRequestHashMismatch() {
    when(values.get(any()))
        .thenReturn(
            Mono.just(
                json(
                    reservation(
                        LoginOtpVerifyReservationStatus.PROCESSING, null,
                        Instant.now().minusSeconds(10), Instant.now().plusSeconds(600)))));
    StepVerifier.create(newAdapter().loadReservation(publicKey, "different-hash"))
        .assertNext(
            r ->
                assertThat(r.outcome())
                    .isEqualTo(LoginOtpVerifyReservationOutcome.REQUEST_HASH_MISMATCH))
        .verifyComplete();
  }

  @Test
  void loadReservationEmptyYieldsCorruptState() {
    when(values.get(any())).thenReturn(Mono.empty());
    StepVerifier.create(newAdapter().loadReservation(publicKey, "req-hash-1"))
        .assertNext(
            r -> assertThat(r.outcome()).isEqualTo(LoginOtpVerifyReservationOutcome.CORRUPT_STATE))
        .verifyComplete();
  }

  // ----- loadConsumptionProof ---------------------------------------------------------

  @Test
  void loadConsumptionProofPresentIsReturned() {
    LoginOtpConsumptionProof proof =
        new LoginOtpConsumptionProof(
            "reservation-hash-1", handoff, operation, "fingerprint-1", Instant.now());
    when(values.get(any()))
        .thenReturn(
            Mono.just(
                json(
                    reservation(
                        LoginOtpVerifyReservationStatus.VERIFICATION_COMMITTED, proof,
                        Instant.now().minusSeconds(10), Instant.now().plusSeconds(600)))));
    StepVerifier.create(newAdapter().loadConsumptionProof(publicKey, "req-hash-1"))
        .assertNext(p -> assertThat(p.challengeFingerprint()).isEqualTo("fingerprint-1"))
        .verifyComplete();
  }

  @Test
  void loadConsumptionProofAbsentYieldsEmpty() {
    when(values.get(any()))
        .thenReturn(
            Mono.just(
                json(
                    reservation(
                        LoginOtpVerifyReservationStatus.PROCESSING, null,
                        Instant.now().minusSeconds(10), Instant.now().plusSeconds(600)))));
    StepVerifier.create(newAdapter().loadConsumptionProof(publicKey, "req-hash-1")).verifyComplete();
  }

  // ----- mutate-based methods: error paths ---------------------------------------------

  @Test
  void mutateWithMissingReservationErrors() {
    when(values.get(any())).thenReturn(Mono.empty());
    StepVerifier.create(
            newAdapter()
                .recordConclusiveFailure(publicKey, "req-hash-1", LoginOtpVerifyPersistenceOutcome.INVALID, 2))
        .expectError(IllegalStateException.class)
        .verify();
  }

  @Test
  void mutateWithHashMismatchErrors() {
    when(values.get(any()))
        .thenReturn(
            Mono.just(
                json(
                    reservation(
                        LoginOtpVerifyReservationStatus.PROCESSING, null,
                        Instant.now().minusSeconds(10), Instant.now().plusSeconds(600)))));
    StepVerifier.create(
            newAdapter()
                .recordConclusiveFailure(publicKey, "wrong-hash", LoginOtpVerifyPersistenceOutcome.INVALID, 2))
        .expectError(IllegalStateException.class)
        .verify();
  }

  @Test
  void mutateWithExpiredTtlErrors() {
    when(values.get(any()))
        .thenReturn(
            Mono.just(
                json(
                    reservation(
                        LoginOtpVerifyReservationStatus.PROCESSING, null,
                        Instant.now().minusSeconds(700), Instant.now().minusSeconds(1)))));
    StepVerifier.create(
            newAdapter()
                .recordConclusiveFailure(publicKey, "req-hash-1", LoginOtpVerifyPersistenceOutcome.INVALID, 2))
        .expectError(IllegalStateException.class)
        .verify();
  }

  // ----- mutate-based methods: success paths ---------------------------------------------

  @Test
  void recordVerificationCommittedSucceeds() {
    when(values.get(any()))
        .thenReturn(
            Mono.just(
                json(
                    reservation(
                        LoginOtpVerifyReservationStatus.PROCESSING, null,
                        Instant.now().minusSeconds(10), Instant.now().plusSeconds(600)))));
    when(values.set(any(), any(), any())).thenReturn(Mono.just(true));
    LoginOtpConsumptionProof proof =
        new LoginOtpConsumptionProof("reservation-hash-1", handoff, operation, "fp-1", Instant.now());
    RecordLoginOtpVerifyCommittedCommand cmd =
        new RecordLoginOtpVerifyCommittedCommand(
            publicKey, "req-hash-1", proof, LoginOtpVerifyPersistenceOutcome.VERIFIED_AND_CONSUMED, 0);
    StepVerifier.create(newAdapter().recordVerificationCommitted(cmd))
        .assertNext(
            r ->
                assertThat(r.status())
                    .isEqualTo(LoginOtpVerifyReservationStatus.VERIFICATION_COMMITTED))
        .verifyComplete();
  }

  @Test
  void recordUnknownOutcomeSucceeds() {
    when(values.get(any()))
        .thenReturn(
            Mono.just(
                json(
                    reservation(
                        LoginOtpVerifyReservationStatus.PROCESSING, null,
                        Instant.now().minusSeconds(10), Instant.now().plusSeconds(600)))));
    when(values.set(any(), any(), any())).thenReturn(Mono.just(true));
    StepVerifier.create(newAdapter().recordUnknownOutcome(publicKey, "req-hash-1"))
        .assertNext(
            r -> assertThat(r.status()).isEqualTo(LoginOtpVerifyReservationStatus.UNKNOWN_OUTCOME))
        .verifyComplete();
  }

  @Test
  void completeWithProofSucceeds() {
    LoginOtpConsumptionProof proof =
        new LoginOtpConsumptionProof("reservation-hash-1", handoff, operation, "fp-1", Instant.now());
    when(values.get(any()))
        .thenReturn(
            Mono.just(
                json(
                    reservation(
                        LoginOtpVerifyReservationStatus.VERIFICATION_COMMITTED, proof,
                        Instant.now().minusSeconds(10), Instant.now().plusSeconds(600)))));
    when(values.set(any(), any(), any())).thenReturn(Mono.just(true));
    StepVerifier.create(newAdapter().complete(publicKey, "req-hash-1"))
        .assertNext(r -> assertThat(r.status()).isEqualTo(LoginOtpVerifyReservationStatus.COMPLETED))
        .verifyComplete();
  }

  @Test
  void completeWithoutProofErrors() {
    when(values.get(any()))
        .thenReturn(
            Mono.just(
                json(
                    reservation(
                        LoginOtpVerifyReservationStatus.PROCESSING, null,
                        Instant.now().minusSeconds(10), Instant.now().plusSeconds(600)))));
    StepVerifier.create(newAdapter().complete(publicKey, "req-hash-1"))
        .expectError(IllegalStateException.class)
        .verify();
  }

  // ----- verify(...) ---------------------------------------------------------

  @Test
  void verifyChallengeNotFoundYieldsChallengeNotFound() {
    when(challenges.findById(any())).thenReturn(Mono.just(Optional.empty()));
    StepVerifier.create(newAdapter().verify(verifyCommand("challenge-1", Instant.now())))
        .assertNext(
            r -> assertThat(r.outcome()).isEqualTo(LoginOtpVerifyPersistenceOutcome.CHALLENGE_NOT_FOUND))
        .verifyComplete();
  }

  @Test
  void verifyVerifiedStatusYieldsExpectedStateChanged() {
    when(challenges.findById(any()))
        .thenReturn(Mono.just(Optional.of(challenge(OtpStatus.VERIFIED, Instant.now().plusSeconds(300), 0, 5))));
    StepVerifier.create(newAdapter().verify(verifyCommand("challenge-1", Instant.now())))
        .assertNext(
            r ->
                assertThat(r.outcome())
                    .isEqualTo(LoginOtpVerifyPersistenceOutcome.EXPECTED_STATE_CHANGED))
        .verifyComplete();
  }

  @Test
  void verifyConsumedStatusYieldsExpectedStateChanged() {
    when(challenges.findById(any()))
        .thenReturn(Mono.just(Optional.of(challenge(OtpStatus.CONSUMED, Instant.now().plusSeconds(300), 0, 5))));
    StepVerifier.create(newAdapter().verify(verifyCommand("challenge-1", Instant.now())))
        .assertNext(
            r ->
                assertThat(r.outcome())
                    .isEqualTo(LoginOtpVerifyPersistenceOutcome.EXPECTED_STATE_CHANGED))
        .verifyComplete();
  }

  @Test
  void verifyLockedStatusYieldsLocked() {
    when(challenges.findById(any()))
        .thenReturn(Mono.just(Optional.of(challenge(OtpStatus.LOCKED, Instant.now().plusSeconds(300), 5, 5))));
    StepVerifier.create(newAdapter().verify(verifyCommand("challenge-1", Instant.now())))
        .assertNext(r -> assertThat(r.outcome()).isEqualTo(LoginOtpVerifyPersistenceOutcome.LOCKED))
        .verifyComplete();
  }

  @Test
  void verifyExpiredStatusYieldsExpired() {
    when(challenges.findById(any()))
        .thenReturn(Mono.just(Optional.of(challenge(OtpStatus.EXPIRED, Instant.now().plusSeconds(300), 0, 5))));
    StepVerifier.create(newAdapter().verify(verifyCommand("challenge-1", Instant.now())))
        .assertNext(r -> assertThat(r.outcome()).isEqualTo(LoginOtpVerifyPersistenceOutcome.EXPIRED))
        .verifyComplete();
  }

  @Test
  void verifyPastExpiryTimestampYieldsExpired() {
    when(challenges.findById(any()))
        .thenReturn(
            Mono.just(Optional.of(challenge(OtpStatus.CREATED, Instant.now().minusSeconds(1), 0, 5))));
    StepVerifier.create(newAdapter().verify(verifyCommand("challenge-1", Instant.now())))
        .assertNext(r -> assertThat(r.outcome()).isEqualTo(LoginOtpVerifyPersistenceOutcome.EXPIRED))
        .verifyComplete();
  }

  @Test
  void verifyOtherStatusYieldsExpectedStateChanged() {
    when(challenges.findById(any()))
        .thenReturn(
            Mono.just(Optional.of(challenge(OtpStatus.INVALIDATED, Instant.now().plusSeconds(300), 0, 5))));
    StepVerifier.create(newAdapter().verify(verifyCommand("challenge-1", Instant.now())))
        .assertNext(
            r ->
                assertThat(r.outcome())
                    .isEqualTo(LoginOtpVerifyPersistenceOutcome.EXPECTED_STATE_CHANGED))
        .verifyComplete();
  }

  @Test
  void verifyMatchYieldsVerifiedAndConsumed() {
    when(challenges.findById(any()))
        .thenReturn(Mono.just(Optional.of(challenge(OtpStatus.CREATED, Instant.now().plusSeconds(300), 0, 5))));
    when(hasher.matches(any(), any())).thenReturn(true);
    when(challenges.update(any())).thenReturn(Mono.empty());
    StepVerifier.create(newAdapter().verify(verifyCommand("challenge-1", Instant.now())))
        .assertNext(
            r -> {
              assertThat(r.outcome())
                  .isEqualTo(LoginOtpVerifyPersistenceOutcome.VERIFIED_AND_CONSUMED);
              assertThat(r.proof()).isNotNull();
            })
        .verifyComplete();
    verify(challenges).update(any());
  }

  @Test
  void verifyMismatchNotLockedYieldsInvalid() {
    when(challenges.findById(any()))
        .thenReturn(Mono.just(Optional.of(challenge(OtpStatus.CREATED, Instant.now().plusSeconds(300), 0, 5))));
    when(hasher.matches(any(), any())).thenReturn(false);
    when(challenges.update(any())).thenReturn(Mono.empty());
    StepVerifier.create(newAdapter().verify(verifyCommand("challenge-1", Instant.now())))
        .assertNext(r -> assertThat(r.outcome()).isEqualTo(LoginOtpVerifyPersistenceOutcome.INVALID))
        .verifyComplete();
  }

  @Test
  void verifyMismatchReachingMaxAttemptsYieldsLocked() {
    when(challenges.findById(any()))
        .thenReturn(Mono.just(Optional.of(challenge(OtpStatus.CREATED, Instant.now().plusSeconds(300), 4, 5))));
    when(hasher.matches(any(), any())).thenReturn(false);
    when(challenges.update(any())).thenReturn(Mono.empty());
    StepVerifier.create(newAdapter().verify(verifyCommand("challenge-1", Instant.now())))
        .assertNext(r -> assertThat(r.outcome()).isEqualTo(LoginOtpVerifyPersistenceOutcome.LOCKED))
        .verifyComplete();
  }

  @Test
  void verifyFindByIdErrorYieldsSystemFailure() {
    when(challenges.findById(any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newAdapter().verify(verifyCommand("challenge-1", Instant.now())))
        .assertNext(
            r -> assertThat(r.outcome()).isEqualTo(LoginOtpVerifyPersistenceOutcome.SYSTEM_FAILURE))
        .verifyComplete();
  }

  @Test
  void verifyUpdateErrorYieldsSystemFailure() {
    when(challenges.findById(any()))
        .thenReturn(Mono.just(Optional.of(challenge(OtpStatus.CREATED, Instant.now().plusSeconds(300), 0, 5))));
    when(hasher.matches(any(), any())).thenReturn(true);
    when(challenges.update(any())).thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newAdapter().verify(verifyCommand("challenge-1", Instant.now())))
        .assertNext(
            r -> assertThat(r.outcome()).isEqualTo(LoginOtpVerifyPersistenceOutcome.SYSTEM_FAILURE))
        .verifyComplete();
  }
}
