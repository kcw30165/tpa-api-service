package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionCasUpdateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionCasUpdateOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.ProtectedAuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.SessionRevision;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * Fail-first, mock-based tests mirroring {@code RedisAuthFlowSessionRevisionMutationAdapterTest}'s
 * style (script 043 evidence): mock {@link ReactiveStringRedisTemplate} and assert on the exact
 * {@link DefaultRedisScript} return-code mapping, rather than a live Redis.
 *
 * <p>Mechanism (confirmed in script 043's dump of replace-revisioned.lua, adapted here WITHOUT the
 * legacy-adoption branch per the ratified no-legacy-migration decision): GET current JSON,
 * cjson.decode it, compare a top-level {@code sessionRevision} field against the caller's expected
 * revision, PTTL-preserve, PSETEX only on match. A missing/malformed revision fails closed
 * (REVISION_MISSING / CORRUPT_STATE), never silently adopted.
 */
@SuppressWarnings("unchecked")
class RedisAuthenticatedSessionCasUpdateAdapterTest {

  private static final AuthenticatedSessionId ID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final SessionRevision EXPECTED = new SessionRevision("a".repeat(32));
  private static final SessionRevision REPLACEMENT = new SessionRevision("b".repeat(32));

  private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
  private final RedisCacheKeyFactory keys = new RedisCacheKeyFactory("ngtpa");

  private RedisAuthenticatedSessionCasUpdateAdapter newAdapter() {
    return new RedisAuthenticatedSessionCasUpdateAdapter(redis, keys);
  }

  private ProtectedAuthenticatedSession protectedSession() {
    var data =
        new ProtectedAuthenticatedSession.Data(
            Map.of(
                ProtectedAuthenticatedSession.CIPHERTEXT, "c",
                ProtectedAuthenticatedSession.ALGORITHM, "AES/GCM/NoPadding",
                ProtectedAuthenticatedSession.NONCE, "n",
                ProtectedAuthenticatedSession.AUTHENTICATION_TAG, "t",
                ProtectedAuthenticatedSession.KEY_VERSION, "v1"));
    Instant now = Instant.parse("2026-09-23T00:00:00Z");
    return new ProtectedAuthenticatedSession(
        ID.value(),
        AuthenticatedSessionStatus.ACTIVE,
        data,
        now,
        now,
        now.plusSeconds(900),
        now.plusSeconds(28800),
        new SessionRevision("0".repeat(32)));
  }

  private AuthenticatedSessionCasUpdateCommand command() {
    return new AuthenticatedSessionCasUpdateCommand(
        ID, EXPECTED, protectedSession(), REPLACEMENT, "req-1");
  }

  private void stubScriptResult(long code) {
    when(redis.execute(any(DefaultRedisScript.class), anyList(), anyList()))
        .thenReturn(Flux.just(code));
  }

  // ----- exact key + capability namespace -------------------------------------

  @Test
  void usesTheSharedAuthenticatedSessionCapabilityKey() {
    stubScriptResult(1L);
    ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
    newAdapter().updateIfCurrent(command()).block();
    org.mockito.Mockito.verify(redis)
        .execute(any(DefaultRedisScript.class), keysCaptor.capture(), anyList());
    assertThat(keysCaptor.getValue())
        .containsExactly(keys.key("auth:authenticated-session", ID.value()));
  }

  // ----- return-code mapping ---------------------------------------------------

  @Test
  void scriptReturnOneMapsToApplied() {
    stubScriptResult(1L);
    StepVerifier.create(newAdapter().updateIfCurrent(command()))
        .assertNext(
            r -> assertThat(r.outcome()).isEqualTo(AuthenticatedSessionCasUpdateOutcome.APPLIED))
        .verifyComplete();
  }

  @Test
  void scriptReturnZeroMapsToRevisionMismatch() {
    stubScriptResult(0L);
    StepVerifier.create(newAdapter().updateIfCurrent(command()))
        .assertNext(
            r ->
                assertThat(r.outcome())
                    .isEqualTo(AuthenticatedSessionCasUpdateOutcome.REVISION_MISMATCH))
        .verifyComplete();
  }

  @Test
  void scriptReturnNegativeOneMapsToNotFound() {
    stubScriptResult(-1L);
    StepVerifier.create(newAdapter().updateIfCurrent(command()))
        .assertNext(
            r -> assertThat(r.outcome()).isEqualTo(AuthenticatedSessionCasUpdateOutcome.NOT_FOUND))
        .verifyComplete();
  }

  @Test
  void scriptReturnNegativeTwoMapsToRevisionMissing() {
    // Confirms fail-closed behavior for a legacy/pre-existing session with no
    // sessionRevision field -- NOT silently adopted, per the ratified decision.
    stubScriptResult(-2L);
    StepVerifier.create(newAdapter().updateIfCurrent(command()))
        .assertNext(
            r ->
                assertThat(r.outcome())
                    .isEqualTo(AuthenticatedSessionCasUpdateOutcome.REVISION_MISSING))
        .verifyComplete();
  }

  @Test
  void scriptReturnNegativeThreeMapsToCorruptState() {
    stubScriptResult(-3L);
    StepVerifier.create(newAdapter().updateIfCurrent(command()))
        .assertNext(
            r ->
                assertThat(r.outcome())
                    .isEqualTo(AuthenticatedSessionCasUpdateOutcome.CORRUPT_STATE))
        .verifyComplete();
  }

  @Test
  void unrecognizedReturnCodeMapsToSystemFailure() {
    stubScriptResult(-99L);
    StepVerifier.create(newAdapter().updateIfCurrent(command()))
        .assertNext(
            r ->
                assertThat(r.outcome())
                    .isEqualTo(AuthenticatedSessionCasUpdateOutcome.SYSTEM_FAILURE))
        .verifyComplete();
  }

  @Test
  void emptyScriptResultMapsToSystemFailure() {
    when(redis.execute(any(DefaultRedisScript.class), anyList(), anyList()))
        .thenReturn(Flux.empty());
    StepVerifier.create(newAdapter().updateIfCurrent(command()))
        .assertNext(
            r ->
                assertThat(r.outcome())
                    .isEqualTo(AuthenticatedSessionCasUpdateOutcome.SYSTEM_FAILURE))
        .verifyComplete();
  }

  @Test
  void redisErrorNeverLeaksAsRawExceptionAndMapsToSystemFailure() {
    when(redis.execute(any(DefaultRedisScript.class), anyList(), anyList()))
        .thenReturn(Flux.error(new IllegalStateException("connection refused: 10.0.0.1:6379")));
    StepVerifier.create(newAdapter().updateIfCurrent(command()))
        .assertNext(
            r -> {
              assertThat(r.outcome())
                  .isEqualTo(AuthenticatedSessionCasUpdateOutcome.SYSTEM_FAILURE);
            })
        .verifyComplete();
  }

  // ----- no legacy-adoption path exists ----------------------------------------

  @Test
  void adapterHasNoLegacyInitializationMethod() {
    // Confirms the deliberate scope decision: no initializeLegacyRevision-equivalent
    // method exists on this class, unlike RedisAuthFlowSessionAdapter.
    var methodNames =
        java.util.Arrays.stream(RedisAuthenticatedSessionCasUpdateAdapter.class.getMethods())
            .map(java.lang.reflect.Method::getName)
            .toList();
    assertThat(methodNames)
        .isNotEmpty()
        .doesNotContain("initializeLegacyRevision", "initializeLegacy");
  }

  // ---- coverage closure (script 063): serialization must be a REAL envelope,
  // not the placeholder (session ID only) confirmed still present in script 062 ----

  @Test
  void replacementValueIsARealSerializedEnvelopeNotABarePlaceholder() {
    stubScriptResult(1L);
    ArgumentCaptor<List<String>> argsCaptor = ArgumentCaptor.forClass(List.class);
    newAdapter().updateIfCurrent(command()).block();
    org.mockito.Mockito.verify(redis)
        .execute(any(DefaultRedisScript.class), anyList(), argsCaptor.capture());

    String serialized = argsCaptor.getValue().get(1);
    // A bare session ID is not valid JSON; a real envelope must parse cleanly.
    ObjectMapper mapper = new ObjectMapper();
    JsonNode node;
    try {
      node = mapper.readTree(serialized);
    } catch (Exception e) {
      throw new AssertionError(
          "replacement value must be a JSON envelope, but failed to parse: " + serialized, e);
    }
    assertThat(node.has("authenticatedSessionId")).isTrue();
    assertThat(node.has("data")).isTrue();
    assertThat(node.get("authenticatedSessionId").asText()).isNotEqualTo(serialized);
  }

  @Test
  void replacementValueStampsTheReplacementRevisionNotTheSessionsOriginalRevision() {
    stubScriptResult(1L);
    ArgumentCaptor<List<String>> argsCaptor = ArgumentCaptor.forClass(List.class);
    newAdapter().updateIfCurrent(command()).block();
    org.mockito.Mockito.verify(redis)
        .execute(any(DefaultRedisScript.class), anyList(), argsCaptor.capture());

    String serialized = argsCaptor.getValue().get(1);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode node;
    try {
      node = mapper.readTree(serialized);
    } catch (Exception e) {
      throw new AssertionError("replacement value must be parseable JSON: " + serialized, e);
    }

    // command() below builds protectedSession() carrying EXPECTED's revision value
    // internally (see the fixture), but the command's own replacementRevision is
    // REPLACEMENT. The serialized sessionRevision must reflect REPLACEMENT, proving
    // the adapter reconciles the two rather than blindly re-serializing whatever
    // revision happened to already be on replacementProtectedSession().
    assertThat(node.get("sessionRevision").asText()).isEqualTo(REPLACEMENT.value());
  }

  @Test
  void replacementValueRoundTripsThroughTheRealJsonMapper() {
    stubScriptResult(1L);
    ArgumentCaptor<List<String>> argsCaptor = ArgumentCaptor.forClass(List.class);
    newAdapter().updateIfCurrent(command()).block();
    org.mockito.Mockito.verify(redis)
        .execute(any(DefaultRedisScript.class), anyList(), argsCaptor.capture());

    String serialized = argsCaptor.getValue().get(1);
    var roundTripped = new RedisAuthenticatedSessionJsonMapper().fromJson(serialized);

    assertThat(roundTripped.authenticatedSessionId()).isEqualTo(ID.value());
    assertThat(roundTripped.sessionRevision()).isEqualTo(REPLACEMENT);
    assertThat(roundTripped.ciphertext()).isEqualTo("c");
  }

  // ----- correlation id never sent to Redis (no leakage into keys/values) ------

  @Test
  void correlationIdIsNeverPassedAsARedisArgument() {
    stubScriptResult(1L);
    ArgumentCaptor<List<String>> argsCaptor = ArgumentCaptor.forClass(List.class);
    newAdapter().updateIfCurrent(command()).block();
    org.mockito.Mockito.verify(redis)
        .execute(any(DefaultRedisScript.class), anyList(), argsCaptor.capture());
    assertThat(argsCaptor.getValue()).noneMatch(v -> v.contains("req-1"));
  }
}
