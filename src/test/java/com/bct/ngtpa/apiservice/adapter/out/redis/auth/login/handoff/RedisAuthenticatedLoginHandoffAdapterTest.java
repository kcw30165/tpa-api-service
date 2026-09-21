package com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.handoff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.RedisLoginIdempotencyKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.auth.login.handoff.AuthenticatedLoginHandoff;
import com.bct.ngtpa.apiservice.application.dto.auth.login.handoff.AuthenticatedLoginHandoffCreateOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.login.handoff.AuthenticatedLoginHandoffStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.login.handoff.CreateAuthenticatedLoginHandoffCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.login.handoff.RecordAuthFlowCleanupProofCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.login.handoff.RecordAuthenticatedSessionCommittedCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.LoginIdempotencyPersistenceId;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.AuthenticatedLoginHandoffReference;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpConsumptionProof;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpVerificationOperationReference;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.service.auth.login.idempotency.LoginIdempotencyPersistenceIdDeriver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Branch-oriented reactive tests for {@link RedisAuthenticatedLoginHandoffAdapter}. Mirrors the
 * proven {@code ReactiveStringRedisTemplate}/{@code ReactiveValueOperations} mocking pattern used
 * elsewhere in this codebase (rounds 28-33). {@code AuthenticatedSessionId} fixture values follow
 * the confirmed validation pattern {@code ^sid_[A-Za-z0-9_-]{20,124}$} (round 42), matching the
 * convention already used in other passing tests in this repo.
 */
@SuppressWarnings("unchecked")
class RedisAuthenticatedLoginHandoffAdapterTest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private final RedisLoginIdempotencyKeyFactory keys = mock(RedisLoginIdempotencyKeyFactory.class);
  private final LoginIdempotencyPersistenceIdDeriver ids =
      mock(LoginIdempotencyPersistenceIdDeriver.class);
  private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
  private final ReactiveValueOperations<String, String> values = mock(ReactiveValueOperations.class);

  private final AuthenticatedLoginHandoffReference handoffRef =
      new AuthenticatedLoginHandoffReference("a".repeat(32));
  private final LoginOtpVerificationOperationReference operationRef =
      new LoginOtpVerificationOperationReference("b".repeat(32));
  private final AuthenticatedSessionId sessionId =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");

  private RedisAuthenticatedLoginHandoffAdapter newAdapter() {
    when(redis.opsForValue()).thenReturn(values);
    when(ids.derive(any(), any()))
        .thenReturn(new LoginIdempotencyPersistenceId("a".repeat(43)));
    when(keys.keyFor(any(), any())).thenReturn("redis-key-1");
    return new RedisAuthenticatedLoginHandoffAdapter(keys, ids, redis, MAPPER);
  }

  private static String json(AuthenticatedLoginHandoff h) {
    try {
      return MAPPER.writeValueAsString(h);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private LoginOtpConsumptionProof proof() {
    return new LoginOtpConsumptionProof(
        "reservation-hash-1", handoffRef, operationRef, "fingerprint-1", Instant.now());
  }

  private AuthenticatedLoginHandoff reservedHandoff() {
    return new AuthenticatedLoginHandoff(
        handoffRef, operationRef, "reservation-hash-1", AuthenticatedLoginHandoffStatus.RESERVED,
        null, null, Instant.now(), Instant.now().plusSeconds(600));
  }

  private AuthenticatedLoginHandoff committedHandoff() {
    return new AuthenticatedLoginHandoff(
        handoffRef, operationRef, "reservation-hash-1",
        AuthenticatedLoginHandoffStatus.AUTHENTICATED_SESSION_COMMITTED,
        sessionId, null, Instant.now(), Instant.now().plusSeconds(600));
  }

  // ----- createIfAbsent ---------------------------------------------------------

  @Test
  void createIfAbsentNewYieldsCreated() {
    when(values.setIfAbsent(any(), any(), any())).thenReturn(Mono.just(true));
    CreateAuthenticatedLoginHandoffCommand cmd =
        new CreateAuthenticatedLoginHandoffCommand(proof(), sessionId, Instant.now().plusSeconds(600));
    StepVerifier.create(newAdapter().createIfAbsent(cmd))
        .assertNext(
            r -> {
              assertThat(r.outcome()).isEqualTo(AuthenticatedLoginHandoffCreateOutcome.CREATED);
              assertThat(r.handoff()).isNotNull();
            })
        .verifyComplete();
  }

  @Test
  void createIfAbsentExistingSameIdentityYieldsExisting() {
    when(values.setIfAbsent(any(), any(), any())).thenReturn(Mono.just(false));
    when(values.get(any())).thenReturn(Mono.just(json(reservedHandoff())));
    CreateAuthenticatedLoginHandoffCommand cmd =
        new CreateAuthenticatedLoginHandoffCommand(proof(), sessionId, Instant.now().plusSeconds(600));
    StepVerifier.create(newAdapter().createIfAbsent(cmd))
        .assertNext(r -> assertThat(r.outcome()).isEqualTo(AuthenticatedLoginHandoffCreateOutcome.EXISTING))
        .verifyComplete();
  }

  @Test
  void createIfAbsentExistingDifferentIdentityYieldsConflict() {
    when(values.setIfAbsent(any(), any(), any())).thenReturn(Mono.just(false));
    AuthenticatedLoginHandoff different =
        new AuthenticatedLoginHandoff(
            handoffRef, operationRef, "different-hash", AuthenticatedLoginHandoffStatus.RESERVED,
            null, null, Instant.now(), Instant.now().plusSeconds(600));
    when(values.get(any())).thenReturn(Mono.just(json(different)));
    CreateAuthenticatedLoginHandoffCommand cmd =
        new CreateAuthenticatedLoginHandoffCommand(proof(), sessionId, Instant.now().plusSeconds(600));
    StepVerifier.create(newAdapter().createIfAbsent(cmd))
        .assertNext(r -> assertThat(r.outcome()).isEqualTo(AuthenticatedLoginHandoffCreateOutcome.CONFLICT))
        .verifyComplete();
  }

  @Test
  void createIfAbsentLoadErrorYieldsConflict() {
    when(values.setIfAbsent(any(), any(), any())).thenReturn(Mono.just(false));
    when(values.get(any())).thenReturn(Mono.empty());
    CreateAuthenticatedLoginHandoffCommand cmd =
        new CreateAuthenticatedLoginHandoffCommand(proof(), sessionId, Instant.now().plusSeconds(600));
    StepVerifier.create(newAdapter().createIfAbsent(cmd))
        .assertNext(r -> assertThat(r.outcome()).isEqualTo(AuthenticatedLoginHandoffCreateOutcome.CONFLICT))
        .verifyComplete();
  }

  // ----- loadByHandoff ---------------------------------------------------------

  @Test
  void loadByHandoffFoundReturnsHandoff() {
    when(values.get(any())).thenReturn(Mono.just(json(reservedHandoff())));
    StepVerifier.create(newAdapter().loadByHandoff(handoffRef))
        .assertNext(h -> assertThat(h.status()).isEqualTo(AuthenticatedLoginHandoffStatus.RESERVED))
        .verifyComplete();
  }

  @Test
  void loadByHandoffMissingErrors() {
    when(values.get(any())).thenReturn(Mono.empty());
    StepVerifier.create(newAdapter().loadByHandoff(handoffRef))
        .expectError(IllegalStateException.class)
        .verify();
  }

  // ----- recordAuthenticatedSessionCommitted ---------------------------------------------------------

  @Test
  void recordAuthenticatedSessionCommittedSucceeds() {
    when(values.get(any())).thenReturn(Mono.just(json(reservedHandoff())));
    when(values.set(any(), any(), any())).thenReturn(Mono.just(true));
    RecordAuthenticatedSessionCommittedCommand cmd =
        new RecordAuthenticatedSessionCommittedCommand(
            handoffRef, operationRef, "reservation-hash-1", sessionId);
    StepVerifier.create(newAdapter().recordAuthenticatedSessionCommitted(cmd))
        .assertNext(
            h ->
                assertThat(h.status())
                    .isEqualTo(AuthenticatedLoginHandoffStatus.AUTHENTICATED_SESSION_COMMITTED))
        .verifyComplete();
  }

  @Test
  void recordAuthenticatedSessionCommittedIdentityConflictErrors() {
    when(values.get(any())).thenReturn(Mono.just(json(reservedHandoff())));
    RecordAuthenticatedSessionCommittedCommand cmd =
        new RecordAuthenticatedSessionCommittedCommand(
            handoffRef, operationRef, "wrong-hash", sessionId);
    StepVerifier.create(newAdapter().recordAuthenticatedSessionCommitted(cmd))
        .expectError(IllegalStateException.class)
        .verify();
  }

  @Test
  void recordAuthenticatedSessionCommittedSessionConflictErrors() {
    when(values.get(any())).thenReturn(Mono.just(json(committedHandoff())));
    AuthenticatedSessionId differentSession =
        new AuthenticatedSessionId("sid_yxwvutsrqponmlkjihgfedcb");
    RecordAuthenticatedSessionCommittedCommand cmd =
        new RecordAuthenticatedSessionCommittedCommand(
            handoffRef, operationRef, "reservation-hash-1", differentSession);
    StepVerifier.create(newAdapter().recordAuthenticatedSessionCommitted(cmd))
        .expectError(IllegalStateException.class)
        .verify();
  }

  @Test
  void recordAuthenticatedSessionCommittedMissingHandoffErrors() {
    when(values.get(any())).thenReturn(Mono.empty());
    RecordAuthenticatedSessionCommittedCommand cmd =
        new RecordAuthenticatedSessionCommittedCommand(
            handoffRef, operationRef, "reservation-hash-1", sessionId);
    StepVerifier.create(newAdapter().recordAuthenticatedSessionCommitted(cmd))
        .expectError(IllegalStateException.class)
        .verify();
  }

  @Test
  void recordAuthenticatedSessionCommittedExpiredTtlErrors() {
    // Build a handoff that satisfies the compact ctor (expiresAt after createdAt) but is
    // already effectively expired relative to "now" inside mutate(): both timestamps are
    // in the past, with expiresAt only 1 second after createdAt.
    AuthenticatedLoginHandoff almostExpired =
        new AuthenticatedLoginHandoff(
            handoffRef, operationRef, "reservation-hash-1", AuthenticatedLoginHandoffStatus.RESERVED,
            null, null, Instant.now().minusSeconds(2), Instant.now().minusSeconds(1));
    when(values.get(any())).thenReturn(Mono.just(json(almostExpired)));
    RecordAuthenticatedSessionCommittedCommand cmd =
        new RecordAuthenticatedSessionCommittedCommand(
            handoffRef, operationRef, "reservation-hash-1", sessionId);
    StepVerifier.create(newAdapter().recordAuthenticatedSessionCommitted(cmd))
        .expectError(IllegalStateException.class)
        .verify();
  }

  // ----- recordCleanupProof ---------------------------------------------------------

  @Test
  void recordCleanupProofSucceeds() {
    when(values.get(any())).thenReturn(Mono.just(json(committedHandoff())));
    when(values.set(any(), any(), any())).thenReturn(Mono.just(true));
    RecordAuthFlowCleanupProofCommand cmd =
        new RecordAuthFlowCleanupProofCommand(handoffRef, sessionId, "cleanup-proof-1", Instant.now());
    StepVerifier.create(newAdapter().recordCleanupProof(cmd))
        .assertNext(h -> assertThat(h.status()).isEqualTo(AuthenticatedLoginHandoffStatus.AUTH_FLOW_DELETED))
        .verifyComplete();
  }

  @Test
  void recordCleanupProofSessionMismatchErrors() {
    when(values.get(any())).thenReturn(Mono.just(json(committedHandoff())));
    AuthenticatedSessionId differentSession =
        new AuthenticatedSessionId("sid_yxwvutsrqponmlkjihgfedcb");
    RecordAuthFlowCleanupProofCommand cmd =
        new RecordAuthFlowCleanupProofCommand(handoffRef, differentSession, "cleanup-proof-1", Instant.now());
    StepVerifier.create(newAdapter().recordCleanupProof(cmd))
        .expectError(IllegalStateException.class)
        .verify();
  }

  @Test
  void recordCleanupProofUncommittedHandoffErrors() {
    when(values.get(any())).thenReturn(Mono.just(json(reservedHandoff())));
    RecordAuthFlowCleanupProofCommand cmd =
        new RecordAuthFlowCleanupProofCommand(handoffRef, sessionId, "cleanup-proof-1", Instant.now());
    StepVerifier.create(newAdapter().recordCleanupProof(cmd))
        .expectError(IllegalStateException.class)
        .verify();
  }
}
