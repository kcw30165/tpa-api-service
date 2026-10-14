package com.bct.ngtpa.apiservice.integration.csrf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisAuthenticatedSessionAdapter;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisAuthenticatedSessionJsonMapper;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.replay.RedisAuthenticatedLoginReplaySessionAdapter;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.login.handoff.AuthenticatedLoginHandoff;
import com.bct.ngtpa.apiservice.application.dto.auth.login.handoff.AuthenticatedLoginHandoffStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.AuthenticatedLoginHandoffReference;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.verify.LoginOtpVerificationOperationReference;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedAccountRef;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionAccount;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.SessionRevision;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedLoginHandoffPort;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.replay.RecoverCommittedLoginByHandoffCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.replay.RecoverCommittedLoginService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.Sha256CsrfTokenHasher;
import com.bct.ngtpa.apiservice.infrastructure.security.session.AesGcmAuthenticatedSessionProtector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reproduces the committed-login replay failure caused by the whole-value CSRF compare-and-set.
 *
 * <p>Everything is real except Redis: JSON mapper, AES-GCM protector, Redis session adapters and
 * Redis CAS adapters. Redis is an in-memory map plus an emulation of the two Lua scripts. The
 * stored record holds the same protected attributes in an order this JVM would not produce.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class CommittedLoginReplayForeignAttributeOrderTest {
  private static final Instant NOW = Instant.parse("2026-10-14T00:00:00Z");
  private static final String RAW_TOKEN = "fresh-replay-csrf-token-0123456789";
  private static final AuthenticatedSessionId SID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final SessionRevision STORED_REVISION =
      new SessionRevision("0123456789abcdef0123456789abcdef");
  private static final AuthenticatedLoginHandoffReference HANDOFF =
      new AuthenticatedLoginHandoffReference("0123456789abcdef0123456789abcdef");
  private static final LoginOtpVerificationOperationReference OPERATION =
      new LoginOtpVerificationOperationReference("abcdef0123456789abcdef0123456789");

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final ObjectMapper mapper = new ObjectMapper();
  private final RedisCacheKeyFactory keys = new RedisCacheKeyFactory("ngtpa");
  private final String key = keys.key("auth:authenticated-session", SID.value());
  private final Map<String, String> redisValues = new HashMap<>();
  private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
  private final ReactiveValueOperations<String, String> values =
      mock(ReactiveValueOperations.class);
  private final RedisAuthenticatedSessionJsonMapper json =
      new RedisAuthenticatedSessionJsonMapper();
  private final AesGcmAuthenticatedSessionProtector protector =
      new AesGcmAuthenticatedSessionProtector(
          Base64.getEncoder().encodeToString(new byte[32]),
          new ObjectMapper().findAndRegisterModules());
  private final RedisAuthenticatedSessionAdapter sessions =
      new RedisAuthenticatedSessionAdapter(new MapCache(redisValues), keys, json);
  private final Sha256CsrfTokenHasher hasher = new Sha256CsrfTokenHasher();
  private final AuthenticatedLoginHandoffPort handoffs = mock(AuthenticatedLoginHandoffPort.class);

  @Test
  void replayRecoversWhenTheStoredAttributeOrderDiffersFromThisJvm() throws Exception {
    givenRedisIsEmulated();
    when(handoffs.loadByHandoff(HANDOFF))
        .thenReturn(
            Mono.just(
                new AuthenticatedLoginHandoff(
                    HANDOFF,
                    OPERATION,
                    "reservation-hash",
                    AuthenticatedLoginHandoffStatus.AUTH_FLOW_DELETED,
                    SID,
                    "cleanup-proof",
                    NOW.minusSeconds(60),
                    NOW.plusSeconds(300))));
    AuthenticatedSession original = session();
    String canonical = json.toJson(protector.protect(original));
    String stored = storedWithForeignAttributeOrder(canonical);
    assertThat(json.fromJson(stored)).as("same record").isEqualTo(json.fromJson(canonical));
    redisValues.put(key, stored);

    var result =
        newService().recover(new RecoverCommittedLoginByHandoffCommand(HANDOFF, "req-1")).block();

    assertThat(result.recovered()).as("recovered").isTrue();
    assertThat(result.authenticatedSessionId()).isEqualTo(SID);
    assertThat(result.authenticatedCsrfToken()).isEqualTo(RAW_TOKEN);
    assertThat(result.accounts()).hasSize(1);
    AuthenticatedSession saved = protector.unprotect(json.fromJson(redisValues.get(key)));
    assertThat(saved.csrfTokenHash()).isEqualTo(hasher.hash(RAW_TOKEN));
    assertThat(saved.revision()).isNotEqualTo(STORED_REVISION);
    assertThat(saved.actorContext()).isEqualTo(original.actorContext());
    assertThat(saved.accounts()).isEqualTo(original.accounts());
    assertThat(saved.createdAt()).isEqualTo(original.createdAt());
    assertThat(saved.lastAccessedAt()).isEqualTo(original.lastAccessedAt());
    assertThat(saved.idleExpiresAt()).isEqualTo(original.idleExpiresAt());
    assertThat(saved.absoluteExpiresAt()).isEqualTo(original.absoluteExpiresAt());
  }

  private RecoverCommittedLoginService newService() {
    return new RecoverCommittedLoginService(
        handoffs,
        new RedisAuthenticatedLoginReplaySessionAdapter(redis, keys, json),
        protector,
        new com.bct.ngtpa.apiservice.application.usecase.session.authenticated
            .AuthenticatedSessionConcurrencyService(
            sessions,
            protector,
            new com.bct.ngtpa.apiservice.adapter.out.redis.auth
                .RedisAuthenticatedSessionCasUpdateAdapter(redis, keys),
            new com.bct.ngtpa.apiservice.application.dto.session.authenticated
                .SecureAuthenticatedSessionRevisionGenerator(),
            clock),
        () -> RAW_TOKEN,
        hasher,
        clock);
  }

  private AuthenticatedSession session() {
    return new AuthenticatedSession(
        SID,
        new ActorContext("member01", ActorUserRole.ROLE_USER_MBR),
        List.of(
            new AuthenticatedSessionAccount(
                new AuthenticatedAccountRef("acc_abcdefghijklmnopqrstuvwx"),
                "DB",
                "00000008802",
                "0001",
                "DBA",
                "OE",
                null,
                null)),
        AuthenticatedSessionStatus.ACTIVE,
        hasher.hash("establish-csrf-token"),
        NOW.minusSeconds(300),
        NOW.minusSeconds(60),
        NOW.plusSeconds(1800),
        NOW.plusSeconds(28800),
        STORED_REVISION,
        null);
  }

  private String storedWithForeignAttributeOrder(String canonical) throws Exception {
    ObjectNode root = (ObjectNode) mapper.readTree(canonical);
    ObjectNode data = (ObjectNode) root.get("data");
    ObjectNode attributes = (ObjectNode) data.get("attributes");
    List<String> names = new ArrayList<>();
    attributes.fieldNames().forEachRemaining(names::add);
    for (List<String> order : permutations(names)) {
      ObjectNode reordered = mapper.createObjectNode();
      for (String name : order) {
        reordered.set(name, attributes.get(name));
      }
      data.set("attributes", reordered);
      String candidate = mapper.writeValueAsString(root);
      if (!candidate.equals(json.toJson(json.fromJson(candidate)))) {
        return candidate;
      }
    }
    throw new AssertionError("no attribute order differs from this JVM's serialization order");
  }

  private static List<List<String>> permutations(List<String> items) {
    List<List<String>> result = new ArrayList<>();
    if (items.isEmpty()) {
      result.add(new ArrayList<>());
      return result;
    }
    for (int i = 0; i < items.size(); i++) {
      List<String> rest = new ArrayList<>(items);
      String head = rest.remove(i);
      for (List<String> tail : permutations(rest)) {
        tail.add(0, head);
        result.add(tail);
      }
    }
    return result;
  }

  private void givenRedisIsEmulated() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.get(ArgumentMatchers.any()))
        .thenAnswer(inv -> Mono.justOrEmpty(redisValues.get((String) inv.getArgument(0))));
    when(redis.execute(
            ArgumentMatchers.any(DefaultRedisScript.class),
            ArgumentMatchers.<String>anyList(),
            ArgumentMatchers.<Object>anyList()))
        .thenAnswer(this::evalScript);
  }

  private Flux<Long> evalScript(InvocationOnMock invocation) throws Exception {
    RedisScript<?> script = invocation.getArgument(0);
    List<String> scriptKeys = invocation.getArgument(1);
    List<?> args = invocation.getArgument(2);
    String scriptKey = scriptKeys.get(0);
    String current = redisValues.get(scriptKey);
    String lua = script.getScriptAsString();
    if (lua.contains("sessionRevision")) {
      if (current == null) {
        return Flux.just(-1L);
      }
      JsonNode revision = mapper.readTree(current).path("sessionRevision");
      if (!revision.isTextual() || revision.asText().isEmpty()) {
        return Flux.just(-2L);
      }
      if (!revision.asText().equals(args.get(0))) {
        return Flux.just(0L);
      }
      redisValues.put(scriptKey, (String) args.get(1));
      return Flux.just(1L);
    }
    if (lua.contains("redis.call('get',KEYS[1])==ARGV[1]")) {
      if (!Objects.equals(current, args.get(0))) {
        return Flux.just(0L);
      }
      redisValues.put(scriptKey, (String) args.get(1));
      return Flux.just(1L);
    }
    throw new AssertionError("unexpected Redis script");
  }

  private static final class MapCache implements CachePort {
    private final Map<String, String> values;

    private MapCache(Map<String, String> values) {
      this.values = values;
    }

    @Override
    public Mono<Optional<String>> get(String cacheKey) {
      return Mono.just(Optional.ofNullable(values.get(cacheKey)));
    }

    @Override
    public Mono<Void> set(String cacheKey, String value, Duration ttl) {
      values.put(cacheKey, value);
      return Mono.empty();
    }

    @Override
    public Mono<Boolean> evict(String cacheKey) {
      return Mono.just(values.remove(cacheKey) != null);
    }
  }
}
