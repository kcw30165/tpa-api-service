package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.*;
import com.bct.ngtpa.apiservice.infrastructure.security.session.AesGcmAuthenticatedSessionProtector;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import reactor.core.publisher.Mono;

/** Opt-in isolated Redis, never the application's configured runtime store. */
@EnabledIfSystemProperty(named = "m15.redis.server", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthenticatedSessionConditionalWriteRedisTest {
  private Process server;
  private LettuceConnectionFactory connection;
  private ReactiveStringRedisTemplate redis;
  private final RedisAuthenticatedSessionJsonMapper mapper = new RedisAuthenticatedSessionJsonMapper();
  private final RedisCacheKeyFactory keys = new RedisCacheKeyFactory("m15-test");
  private final AuthenticatedSessionProtectorPort protector =
      new AesGcmAuthenticatedSessionProtector(
          Base64.getEncoder().encodeToString(new byte[32]), new ObjectMapper());
  private static final AuthenticatedSessionId ID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final SessionRevision INITIAL = new SessionRevision("a".repeat(32));
  private final String key = keys.key("auth:authenticated-session", ID.value());
  private final AtomicInteger revisions = new AtomicInteger();
  private final AtomicReference<Supplier<Mono<?>>> beforeWrite = new AtomicReference<>();
  private final List<AuthenticatedSessionCasUpdateCommand> commands = new ArrayList<>();
  private final List<AuthenticatedSessionCasUpdateOutcome> outcomes = new ArrayList<>();
  private RedisAuthenticatedSessionCasUpdateAdapter adapter;
  private AuthenticatedSessionPort sessions;
  private AuthenticatedSessionConcurrencyService concurrency;
  private MutableClock clock;

  @BeforeAll
  void startIsolatedRedis(@TempDir Path directory) throws Exception {
    int port;
    try (var socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    var builder =
        new ProcessBuilder(
            System.getProperty("m15.redis.server"),
            "--bind", "127.0.0.1",
            "--port", Integer.toString(port),
            "--save", "",
            "--appendonly", "no",
            "--protected-mode", "yes",
            "--dir", directory.toString())
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD);
    String libraryPath = System.getProperty("m15.redis.libraryPath");
    if (libraryPath != null) {
      builder.environment().put("LD_LIBRARY_PATH", libraryPath);
    }
    server = builder.start();
    connection = new LettuceConnectionFactory("127.0.0.1", port);
    connection.afterPropertiesSet();
    connection.start();
    redis = new ReactiveStringRedisTemplate(connection);
    Instant limit = Instant.now().plusSeconds(10);
    while (true) {
      try {
        assertThat(redis.getConnectionFactory().getReactiveConnection().ping().block(Duration.ofSeconds(1)))
            .isEqualTo("PONG");
        break;
      } catch (RuntimeException error) {
        if (!server.isAlive() || Instant.now().isAfter(limit)) {
          throw new IllegalStateException("Isolated Redis did not become ready");
        }
        Thread.sleep(25);
      }
    }
  }

  @AfterAll
  void stopIsolatedRedis() throws Exception {
    if (connection != null) {
      connection.destroy();
    }
    if (server != null) {
      server.destroy();
      if (!server.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
        server.destroyForcibly().waitFor();
      }
    }
  }

  @BeforeEach
  void prepare() {
    redis.delete(key).block();
    commands.clear();
    outcomes.clear();
    beforeWrite.set(null);
    clock = new MutableClock(Instant.ofEpochMilli(redisNow()));
    adapter = new RedisAuthenticatedSessionCasUpdateAdapter(redis, keys);
    sessions = mock(AuthenticatedSessionPort.class);
    when(sessions.load(any()))
        .thenAnswer(
            invocation ->
                redis.opsForValue().get(key)
                    .map(mapper::fromJson)
                    .map(Optional::of)
                    .defaultIfEmpty(Optional.empty()));
    AuthenticatedSessionCasUpdatePort intercepted =
        command ->
            Mono.defer(
                () -> {
                  commands.add(command);
                  Supplier<Mono<?>> hook = beforeWrite.getAndSet(null);
                  Mono<?> prerequisite = hook == null ? Mono.empty() : hook.get();
                  return prerequisite.then(adapter.updateIfCurrent(command))
                      .doOnNext(result -> outcomes.add(result.outcome()));
                });
    concurrency =
        new AuthenticatedSessionConcurrencyService(
            sessions,
            protector,
            intercepted,
            () -> new SessionRevision(String.format("%032x", revisions.incrementAndGet())),
            clock);
  }

  @Test
  void preserveCurrentPolicyKeepsExactExpiryInsideRedis() {
    var original = seed();
    long deadline = expiry();
    var result = concurrency.updateIfUsable(ID, s -> s.withFeatureState("cache-a", "new"), "test").block();
    assertThat(result.outcome()).isEqualTo(AuthenticatedSessionConcurrencyOutcome.APPLIED);
    assertThat(expiry()).isEqualTo(deadline);
    assertThat(ttl()).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(40).toMillis());
    assertThat(stored().featureState()).containsEntry("unrelated", "keep").containsEntry("cache-a", "new");
    assertThat(stored().revision()).isNotEqualTo(original.revision());
  }

  @Test
  void absolutePolicyWritesExactDeadlineNotAJavaRelativeTtl() {
    seed();
    Instant deadline = clock.instant().plusSeconds(90);
    var result = concurrency.updateIfEligible(
        ID, (s, now) -> !s.status().terminal(),
        (s, now) -> s.withFeatureState("cache-a", "new"), s -> deadline, "test").block();
    assertThat(result.outcome()).isEqualTo(AuthenticatedSessionConcurrencyOutcome.APPLIED);
    assertThat(expiry()).isEqualTo(deadline.toEpochMilli());
    assertThat(ttl()).isBetween(85_000L, 90_000L);
    assertThat(commands.getFirst().expiryDeadline()).isEqualTo(deadline);
  }

  @Test
  void ineligibleObservationReturnsCurrentStateWithoutWriting() {
    seed();
    var result = concurrency.updateIfEligible(
        ID, (s, now) -> false,
        (s, now) -> { throw new AssertionError("Ineligible mutator ran"); },
        s -> { throw new AssertionError("Ineligible TTL policy ran"); }, "test").block();
    assertThat(result.outcome()).isEqualTo(AuthenticatedSessionConcurrencyOutcome.UNCHANGED);
    assertThat(result.session().revision()).isEqualTo(INITIAL);
    assertThat(commands).isEmpty();
  }

  @Test
  void missingSessionCannotBeCreatedByEitherPolicy() {
    var current = session();
    for (Instant deadline : Arrays.asList(null, clock.instant().plusSeconds(90))) {
      var command = command(current, deadline);
      assertThat(adapter.updateIfCurrent(command).block().outcome())
          .isEqualTo(AuthenticatedSessionCasUpdateOutcome.NOT_FOUND);
      assertThat(redis.hasKey(key).block()).isFalse();
    }
    var result = concurrency.updateIfEligible(
        ID, (s, now) -> true, (s, now) -> s, s -> clock.instant().plusSeconds(90), "test").block();
    assertThat(result.outcome()).isEqualTo(AuthenticatedSessionConcurrencyOutcome.SESSION_NOT_FOUND);
  }

  @Test
  void elapsedAbsoluteDeadlineIsRejectedWithoutAnyWrite() {
    var original = seed();
    String before = redis.opsForValue().get(key).block();
    long expiry = expiry();
    var result = adapter.updateIfCurrent(command(original, Instant.ofEpochMilli(redisNow() - 1))).block();
    assertThat(result.outcome()).isEqualTo(AuthenticatedSessionCasUpdateOutcome.SYSTEM_FAILURE);
    assertThat(redis.opsForValue().get(key).block()).isEqualTo(before);
    assertThat(expiry()).isEqualTo(expiry);
  }

  @Test
  void bothScriptsRejectMalformedOrMissingRevisionWithoutAdoption() throws Exception {
    var current = session();
    for (String revision : List.of("", "not-a-revision", "A".repeat(32), "a".repeat(31))) {
      var node = new ObjectMapper().readTree(mapper.toJson(protector.protect(current)));
      ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("sessionRevision", revision);
      for (Instant deadline : Arrays.asList(null, clock.instant().plusSeconds(90))) {
        redis.opsForValue().set(key, node.toString(), Duration.ofSeconds(40)).block();
        assertThat(adapter.updateIfCurrent(command(current, deadline)).block().outcome())
            .isEqualTo(AuthenticatedSessionCasUpdateOutcome.REVISION_MISSING);
        assertThat(redis.opsForValue().get(key).block()).isEqualTo(node.toString());
      }
    }
  }

  @Test
  void delayedExpiredCommandNeverCreatesRelativeTtlExtension() {
    var current = seed();
    Instant deadline = Instant.ofEpochMilli(redisNow() + 150);
    var pending = command(current, deadline);
    awaitDeadline(deadline);
    assertThat(adapter.updateIfCurrent(pending).block().outcome())
        .isEqualTo(AuthenticatedSessionCasUpdateOutcome.SYSTEM_FAILURE);
    assertThat(stored().revision()).isEqualTo(INITIAL);
  }

  private AuthenticatedSessionCasUpdateCommand command(AuthenticatedSession current, Instant deadline) {
    var replacement = new SessionRevision("b".repeat(32));
    return new AuthenticatedSessionCasUpdateCommand(
        ID, current.revision(), protector.protect(current), replacement, "test", deadline);
  }

  private AuthenticatedSession seed() {
    var session = session();
    redis.opsForValue().set(key, mapper.toJson(protector.protect(session)), Duration.ofSeconds(40)).block();
    return session;
  }

  private AuthenticatedSession session() {
    Instant now = clock.instant();
    return new AuthenticatedSession(
        ID, new ActorContext("synthetic-member", ActorUserRole.ROLE_USER_MBR), List.of(),
        AuthenticatedSessionStatus.ACTIVE, "synthetic-hash", now.minusSeconds(60), now.minusSeconds(10),
        now.plusSeconds(40), now.plusSeconds(300), INITIAL, null,
        Map.of("unrelated", "keep", "cache-a", "old"));
  }

  private AuthenticatedSession stored() {
    return protector.unprotect(mapper.fromJson(redis.opsForValue().get(key).block()));
  }

  private long redisNow() {
    return evaluate("local t = redis.call('TIME'); return t[1]*1000 + math.floor(t[2]/1000)");
  }

  private long expiry() {
    return evaluate("return redis.call('PEXPIRETIME', KEYS[1])");
  }

  private long ttl() {
    return redis.getExpire(key).block().toMillis();
  }

  private long evaluate(String lua) {
    return redis.execute(new DefaultRedisScript<Long>(lua, Long.class), List.of(key)).next().block();
  }

  private void awaitDeadline(Instant deadline) {
    long limit = System.nanoTime() + Duration.ofSeconds(3).toNanos();
    while (redisNow() <= deadline.toEpochMilli()) {
      if (System.nanoTime() > limit) {
        throw new AssertionError("Redis TIME did not reach test deadline");
      }
    }
  }

  private static final class MutableClock extends Clock {
    private Instant now;
    MutableClock(Instant now) { this.now = now; }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return now; }
  }
}
