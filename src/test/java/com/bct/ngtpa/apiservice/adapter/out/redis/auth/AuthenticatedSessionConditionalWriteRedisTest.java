package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.redis.RedisStringCacheAdapter;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.PersonalInformationConfigCacheEntry;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonOptionsCacheEntry;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.idempotency.JacksonTransientJsonCodec;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.csrf.AuthenticatedSessionCsrfRefreshService;
import com.bct.ngtpa.apiservice.application.usecase.fundcomparison.FundComparisonOptionsCacheService;
import com.bct.ngtpa.apiservice.application.usecase.personalinformation.PersonalInformationConfigCacheService;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.usecase.passwordvalidation.PasswordValidationThrottleService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.Sha256CsrfTokenHasher;
import com.bct.ngtpa.apiservice.infrastructure.security.session.AesGcmAuthenticatedSessionProtector;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

/** Opt-in isolated Redis, never the application's configured runtime store. */
@EnabledIfSystemProperty(named = "m15.redis.server", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(30)
class AuthenticatedSessionConditionalWriteRedisTest {
  private Process server;
  private LettuceConnectionFactory connection;
  private ReactiveStringRedisTemplate redis;
  private final RedisAuthenticatedSessionJsonMapper mapper =
      new RedisAuthenticatedSessionJsonMapper();
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
  private final List<AuthenticatedSessionCasUpdateCommand> commands = new CopyOnWriteArrayList<>();
  private final List<AuthenticatedSessionCasUpdateOutcome> outcomes = new CopyOnWriteArrayList<>();
  private final AtomicBoolean loseWriteResponse = new AtomicBoolean();
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
                "--bind",
                "127.0.0.1",
                "--port",
                Integer.toString(port),
                "--save",
                "",
                "--appendonly",
                "no",
                "--protected-mode",
                "yes",
                "--dir",
                directory.toString())
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
    assertThat(
            Mono.defer(() -> redis.getConnectionFactory().getReactiveConnection().ping())
                .timeout(Duration.ofSeconds(1))
                .retryWhen(
                    Retry.fixedDelay(400, Duration.ofMillis(25)).filter(error -> server.isAlive()))
                .block(Duration.ofSeconds(10)))
        .isEqualTo("PONG");
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
    loseWriteResponse.set(false);
    clock = new MutableClock(Instant.ofEpochMilli(redisNow()));
    adapter = new RedisAuthenticatedSessionCasUpdateAdapter(redis, keys);
    sessions =
        spy(new RedisAuthenticatedSessionAdapter(new RedisStringCacheAdapter(redis), keys, mapper));
    doAnswer(
            invocation -> {
              Mono<AuthenticatedSessionPersistenceResult> actual =
                  (Mono<AuthenticatedSessionPersistenceResult>) invocation.callRealMethod();
              return Mono.defer(
                  () -> {
                    Supplier<Mono<?>> hook = beforeWrite.getAndSet(null);
                    return (hook == null ? Mono.empty() : hook.get()).then(actual);
                  });
            })
        .when(sessions)
        .touch(any());
    AuthenticatedSessionCasUpdatePort intercepted =
        command ->
            Mono.defer(
                () -> {
                  commands.add(command);
                  Supplier<Mono<?>> hook = beforeWrite.getAndSet(null);
                  Mono<?> prerequisite = hook == null ? Mono.empty() : hook.get();
                  return prerequisite
                      .then(adapter.updateIfCurrent(command))
                      .doOnNext(result -> outcomes.add(result.outcome()))
                      .flatMap(
                          result ->
                              loseWriteResponse.getAndSet(false)
                                  ? Mono.error(
                                      new TimeoutException("Synthetic lost CAS acknowledgement"))
                                  : Mono.just(result));
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
    var result =
        concurrency.updateIfUsable(ID, s -> s.withFeatureState("cache-a", "new"), "test").block();
    assertThat(result.outcome()).isEqualTo(AuthenticatedSessionConcurrencyOutcome.APPLIED);
    assertThat(expiry()).isEqualTo(deadline);
    assertThat(ttl()).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(40).toMillis());
    assertThat(stored().featureState())
        .containsEntry("unrelated", "keep")
        .containsEntry("cache-a", "new");
    assertThat(stored().revision()).isNotEqualTo(original.revision());
  }

  @Test
  void absolutePolicyWritesExactDeadlineNotAJavaRelativeTtl() {
    seed();
    Instant deadline = clock.instant().plusSeconds(90);
    var result =
        concurrency
            .updateIfEligible(
                ID,
                (s, now) -> !s.status().terminal(),
                (s, now) -> s.withFeatureState("cache-a", "new"),
                s -> deadline,
                "test")
            .block();
    assertThat(result.outcome()).isEqualTo(AuthenticatedSessionConcurrencyOutcome.APPLIED);
    assertThat(expiry()).isEqualTo(deadline.toEpochMilli());
    assertThat(ttl()).isBetween(85_000L, 90_000L);
    assertThat(commands.getFirst().expiryDeadline()).isEqualTo(deadline);
  }

  @Test
  void ineligibleObservationReturnsCurrentStateWithoutWriting() {
    seed();
    var result =
        concurrency
            .updateIfEligible(
                ID,
                (s, now) -> false,
                (s, now) -> {
                  throw new AssertionError("Ineligible mutator ran");
                },
                s -> {
                  throw new AssertionError("Ineligible TTL policy ran");
                },
                "test")
            .block();
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
    var result =
        concurrency
            .updateIfEligible(
                ID, (s, now) -> true, (s, now) -> s, s -> clock.instant().plusSeconds(90), "test")
            .block();
    assertThat(result.outcome())
        .isEqualTo(AuthenticatedSessionConcurrencyOutcome.SESSION_NOT_FOUND);
  }

  @Test
  void elapsedAbsoluteDeadlineIsRejectedWithoutAnyWrite() {
    var original = seed();
    String before = redis.opsForValue().get(key).block();
    long expiry = expiry();
    var result =
        adapter.updateIfCurrent(command(original, Instant.ofEpochMilli(redisNow() - 1))).block();
    assertThat(result.outcome()).isEqualTo(AuthenticatedSessionCasUpdateOutcome.SYSTEM_FAILURE);
    assertThat(redis.opsForValue().get(key).block()).isEqualTo(before);
    assertThat(expiry()).isEqualTo(expiry);
  }

  @Test
  void bothScriptsRejectMalformedOrMissingRevisionWithoutAdoption() throws Exception {
    var current = session();
    for (String revision :
        Arrays.asList(null, "", "not-a-revision", "A".repeat(32), "a".repeat(31))) {
      var node = new ObjectMapper().readTree(mapper.toJson(protector.protect(current)));
      if (revision == null) {
        ((com.fasterxml.jackson.databind.node.ObjectNode) node).remove("sessionRevision");
      } else {
        ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("sessionRevision", revision);
      }
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

  @Test
  void usableMutationLosesToTerminalWriterAndReloadRejectsIt() {
    seed();
    beforeWrite.set(
        () -> lifecycle().logout(new LogoutActiveAuthenticatedSessionCommand(ID, "terminal")));
    var result =
        concurrency
            .updateIfUsable(ID, s -> s.withFeatureState("cache-a", "stale"), "cache")
            .block();
    assertThat(result.outcome())
        .isEqualTo(AuthenticatedSessionConcurrencyOutcome.SESSION_NOT_USABLE);
    assertThat(stored().status()).isEqualTo(AuthenticatedSessionStatus.LOGGED_OUT);
    assertThat(stored().featureState())
        .containsEntry("cache-a", "old")
        .containsEntry("unrelated", "keep");
    assertThat(outcomes)
        .containsExactly(
            AuthenticatedSessionCasUpdateOutcome.APPLIED,
            AuthenticatedSessionCasUpdateOutcome.REVISION_MISMATCH);
    verify(sessions, never()).touch(any());
  }

  @ParameterizedTest
  @EnumSource(
      value = AuthenticatedSessionStatus.class,
      names = {"LOGGED_OUT", "REVOKED", "EXPIRED"})
  void everyTerminalTransitionChangesRevisionAndShortensActualTtl(
      AuthenticatedSessionStatus target) {
    seed(target == AuthenticatedSessionStatus.EXPIRED ? expiredSession(false) : session());
    long oldExpiry = expiry();
    Instant transitionAt = clock.instant();
    assertThat(terminal(target).block().status().name()).isEqualTo(target.name());
    assertThat(stored().revision()).isNotEqualTo(INITIAL);
    assertThat(stored().status()).isEqualTo(target);
    assertThat(expiry())
        .isEqualTo(transitionAt.plusSeconds(20).toEpochMilli())
        .isLessThan(oldExpiry);
    assertThat(ttl()).isBetween(15_000L, 20_000L);
    assertThat(stored().featureState()).containsEntry("unrelated", "keep");
    verify(sessions, never()).touch(any());
  }

  @ParameterizedTest
  @EnumSource(
      value = AuthenticatedSessionStatus.class,
      names = {"LOGGED_OUT", "REVOKED", "EXPIRED"})
  void staleTouchCannotRestoreTerminalState(AuthenticatedSessionStatus target) {
    seed();
    beforeWrite.set(
        () -> {
          if (target == AuthenticatedSessionStatus.EXPIRED) {
            clock.now = clock.now.plusSeconds(41);
          }
          return terminal(target);
        });
    var result = touch().block();
    assertThat(result.status().name()).isEqualTo(target.name());
    assertThat(stored().status()).isEqualTo(target);
    assertThat(outcomes)
        .containsExactly(
            AuthenticatedSessionCasUpdateOutcome.APPLIED,
            AuthenticatedSessionCasUpdateOutcome.REVISION_MISMATCH);
    assertThat(commands).hasSize(2);
    verify(sessions, never()).touch(any());
  }

  @Test
  void staleTouchReloadsAndCannotRestoreAnIndependentlyTombstonedFeatureEntry() {
    seed();
    beforeWrite.set(
        () ->
            concurrency.updateIfUsable(
                ID, s -> s.withFeatureState("cache-a", "tombstone"), "other-writer"));
    var result = touch().block();
    assertThat(result.status()).isEqualTo(AuthenticatedSessionLifecycleStatus.ACTIVE);
    assertThat(result.session().featureState())
        .containsEntry("cache-a", "tombstone")
        .containsEntry("unrelated", "keep");
    assertThat(stored().featureState()).isEqualTo(result.session().featureState());
    assertThat(outcomes)
        .containsExactly(
            AuthenticatedSessionCasUpdateOutcome.APPLIED,
            AuthenticatedSessionCasUpdateOutcome.REVISION_MISMATCH,
            AuthenticatedSessionCasUpdateOutcome.APPLIED);
    assertThat(commands).hasSize(3);
    verify(sessions, never()).touch(any());
  }

  @Test
  void touchExtendsActualRedisTtlUsingTheReplacementIdleDeadline() {
    seed();
    long oldExpiry = expiry();
    var result = touch().block();
    assertThat(result.status()).isEqualTo(AuthenticatedSessionLifecycleStatus.ACTIVE);
    assertThat(result.session().idleExpiresAt()).isEqualTo(clock.instant().plusSeconds(120));
    assertThat(expiry())
        .isEqualTo(result.session().idleExpiresAt().toEpochMilli())
        .isGreaterThan(oldExpiry);
    assertThat(ttl()).isBetween(115_000L, 120_000L);
    assertThat(stored().revision()).isNotEqualTo(INITIAL);
    verify(sessions, never()).touch(any());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void discoveredIdleOrAbsoluteExpiryUsesTerminalEligibilityAndRetention(boolean absolute) {
    seed(expiredSession(absolute));
    var result = touch().block();
    assertThat(result.status()).isEqualTo(AuthenticatedSessionLifecycleStatus.EXPIRED);
    assertThat(stored().status()).isEqualTo(AuthenticatedSessionStatus.EXPIRED);
    assertThat(stored().revision()).isNotEqualTo(INITIAL);
    assertThat(expiry()).isEqualTo(clock.instant().plusSeconds(20).toEpochMilli());
    assertThat(ttl()).isBetween(15_000L, 20_000L);
    verify(sessions, never()).touch(any());
  }

  @Test
  void expiredSessionRemainsIneligibleForExistingUsableMutationContract() {
    seed(expiredSession(false));
    var result =
        concurrency
            .updateIfUsable(
                ID,
                s -> {
                  throw new AssertionError("Expired usable-session mutator ran");
                },
                "test")
            .block();
    assertThat(result.outcome())
        .isEqualTo(AuthenticatedSessionConcurrencyOutcome.SESSION_NOT_USABLE);
    assertThat(result.session()).isNull();
    assertThat(commands).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(
      value = AuthenticatedSessionStatus.class,
      names = {"LOGGED_OUT", "REVOKED", "EXPIRED"})
  void repeatedTerminalObservationsNeverWriteOrExtendRetention(AuthenticatedSessionStatus target) {
    seed(target == AuthenticatedSessionStatus.EXPIRED ? expiredSession(false) : session());
    terminal(target).block();
    String first = redis.opsForValue().get(key).block();
    long deadline = expiry();
    int writes = commands.size();
    clock.now = clock.now.plusSeconds(5);
    assertThat(terminal(target).block().status().name()).isEqualTo(target.name());
    assertThat(touch().block().status().name()).isEqualTo(target.name());
    assertThat(
            lifecycle()
                .revoke(new RevokeActiveAuthenticatedSessionCommand(ID, "test", "repeat"))
                .block()
                .status()
                .name())
        .isEqualTo(target.name());
    assertThat(redis.opsForValue().get(key).block()).isEqualTo(first);
    assertThat(expiry()).isEqualTo(deadline);
    assertThat(commands).hasSize(writes);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void touchNeverExtendsPastAbsoluteExpiryIncludingConflictRetry(boolean conflict) {
    var original = session();
    Instant absolute = clock.instant().plusSeconds(60);
    seed(copyTimes(original, original.idleExpiresAt(), absolute));
    if (conflict) {
      beforeWrite.set(
          () ->
              concurrency
                  .updateIfUsable(ID, s -> s.withFeatureState("cache-a", "fresh"), "conflict")
                  .doOnNext(ignored -> clock.now = clock.now.plusSeconds(10)));
    }
    var result = touch().block();
    assertThat(result.status()).isEqualTo(AuthenticatedSessionLifecycleStatus.ACTIVE);
    assertThat(result.session().idleExpiresAt()).isEqualTo(absolute);
    assertThat(expiry()).isEqualTo(absolute.toEpochMilli());
    assertThat(ttl()).isBetween(55_000L, 60_000L);
    if (conflict) {
      assertThat(outcomes)
          .containsExactly(
              AuthenticatedSessionCasUpdateOutcome.APPLIED,
              AuthenticatedSessionCasUpdateOutcome.REVISION_MISMATCH,
              AuthenticatedSessionCasUpdateOutcome.APPLIED);
      assertThat(stored().featureState()).containsEntry("cache-a", "fresh");
      assertThat(
              commands.stream()
                  .filter(c -> c.expiryDeadline() != null)
                  .map(AuthenticatedSessionCasUpdateCommand::expiryDeadline))
          .containsExactly(absolute, absolute);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void terminalDeadlineIsStableAcrossConflictAndExecutionDelay(boolean discoveredExpiry) {
    seed(discoveredExpiry ? expiredSession(false) : session());
    Instant transition = clock.instant();
    beforeWrite.set(
        () ->
            concurrency
                .updateIfEligible(
                    ID,
                    (s, now) -> true,
                    (s, now) -> s.withFeatureState("cache-a", "fresh"),
                    s -> null,
                    "other")
                .doOnNext(ignored -> clock.now = clock.now.plusSeconds(5))
                .then(
                    Mono.fromRunnable(() -> awaitDeadline(Instant.ofEpochMilli(redisNow() + 50)))
                        .subscribeOn(Schedulers.boundedElastic())));
    var result =
        discoveredExpiry
            ? touch().block()
            : terminal(AuthenticatedSessionStatus.LOGGED_OUT).block();
    assertThat(result.status())
        .isEqualTo(
            discoveredExpiry
                ? AuthenticatedSessionLifecycleStatus.EXPIRED
                : AuthenticatedSessionLifecycleStatus.LOGGED_OUT);
    assertThat(expiry()).isEqualTo(transition.plusSeconds(20).toEpochMilli());
    assertThat(
            commands.stream()
                .filter(c -> c.expiryDeadline() != null)
                .map(AuthenticatedSessionCasUpdateCommand::expiryDeadline))
        .containsExactly(transition.plusSeconds(20), transition.plusSeconds(20));
    assertThat(outcomes)
        .containsExactly(
            AuthenticatedSessionCasUpdateOutcome.APPLIED,
            AuthenticatedSessionCasUpdateOutcome.REVISION_MISMATCH,
            AuthenticatedSessionCasUpdateOutcome.APPLIED);
    assertThat(stored().featureState())
        .containsEntry("cache-a", "fresh")
        .containsEntry("unrelated", "keep");
  }

  @Test
  void delayedTouchUsesOriginalDeadlineAndRejectsElapsedOrSkewedDeadline() {
    seed();
    Instant deadline = clock.instant().plusSeconds(120);
    beforeWrite.set(
        () ->
            Mono.fromRunnable(() -> awaitDeadline(clock.instant().plusMillis(100)))
                .subscribeOn(Schedulers.boundedElastic()));
    assertThat(touch().block().status()).isEqualTo(AuthenticatedSessionLifecycleStatus.ACTIVE);
    assertThat(expiry()).isEqualTo(deadline.toEpochMilli());
    var s = session();
    seed(
        new AuthenticatedSession(
            ID,
            s.actorContext(),
            s.accounts(),
            s.status(),
            s.csrfTokenHash(),
            clock.instant().minusSeconds(600),
            clock.instant().minusSeconds(400),
            s.idleExpiresAt(),
            s.absoluteExpiresAt(),
            s.revision(),
            s.fundPriceModeMetadata(),
            s.featureState()));
    String before = redis.opsForValue().get(key).block();
    int writes = commands.size();
    clock.now = clock.now.minusSeconds(200);
    assertThat(touch().block().status()).isEqualTo(AuthenticatedSessionLifecycleStatus.FAILED);
    assertThat(redis.opsForValue().get(key).block()).isEqualTo(before);
    assertThat(commands).hasSize(writes + 1);
    assertThat(outcomes.getLast()).isEqualTo(AuthenticatedSessionCasUpdateOutcome.SYSTEM_FAILURE);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void lostWriteAcknowledgementFailsClosedAndNeverRetries(boolean terminal) {
    seed();
    loseWriteResponse.set(true);
    var result =
        terminal ? terminal(AuthenticatedSessionStatus.LOGGED_OUT).block() : touch().block();
    assertThat(result.status()).isEqualTo(AuthenticatedSessionLifecycleStatus.FAILED);
    assertThat(result.session()).isNull();
    assertThat(commands).hasSize(1);
    assertThat(outcomes).containsExactly(AuthenticatedSessionCasUpdateOutcome.APPLIED);
    assertThat(stored().status())
        .isEqualTo(
            terminal ? AuthenticatedSessionStatus.LOGGED_OUT : AuthenticatedSessionStatus.ACTIVE);
  }

  @Test
  void twelveParallelTouchesRemainActiveAfterDeterministicConflictExhaustion() {
    seed();
    var contention = new ContendedTouches(Mono.empty());
    var results = contention.run();
    assertThat(results)
        .hasSize(12)
        .allSatisfy(
            result -> {
              assertThat(result.status()).isEqualTo(AuthenticatedSessionLifecycleStatus.ACTIVE);
              assertThat(result.session().isUsable(clock.instant())).isTrue();
            });
    assertThat(contention.conflicts.get()).isEqualTo(30);
    assertThat(contention.applied.get()).isEqualTo(3);
    assertThat(results.stream().filter(r -> r.session().revision().equals(stored().revision())))
        .hasSize(10);
    assertThat(stored().isUsable(clock.instant())).isTrue();
    assertThat(stored().featureState()).containsEntry("unrelated", "keep");
    assertThat(expiry()).isEqualTo(stored().idleExpiresAt().toEpochMilli());
  }

  @ParameterizedTest
  @EnumSource(
      value = AuthenticatedSessionStatus.class,
      names = {"LOGGED_OUT", "REVOKED", "EXPIRED"})
  void parallelTouchesCannotRestoreTerminalWriterAtExhaustion(AuthenticatedSessionStatus target) {
    seed();
    var contention =
        new ContendedTouches(
            Mono.defer(
                () -> {
                  if (target == AuthenticatedSessionStatus.EXPIRED) {
                    clock.now = clock.now.plusSeconds(121);
                  }
                  return terminal(target);
                }));
    var results = contention.run();
    assertThat(results).hasSize(12);
    assertThat(
            results.stream().filter(r -> r.status() == AuthenticatedSessionLifecycleStatus.ACTIVE))
        .hasSize(2);
    assertThat(results.stream().filter(r -> r.status().name().equals(target.name()))).hasSize(10);
    assertThat(stored().status()).isEqualTo(target);
    assertThat(expiry()).isEqualTo(clock.instant().plusSeconds(20).toEpochMilli());
    assertThat(contention.applied.get()).isEqualTo(2);
    assertThat(contention.conflicts.get()).isEqualTo(31);
    verify(sessions, never()).touch(any());
  }

  @Test
  void parallelTouchesPreserveAppliedFeatureMutationIncludingExhaustionReloads() {
    seed();
    var contention =
        new ContendedTouches(
            concurrency
                .updateIfUsable(
                    ID, s -> s.withFeatureState("cache-a", "concurrent-value"), "feature")
                .doOnNext(
                    result ->
                        assertThat(result.outcome())
                            .isEqualTo(AuthenticatedSessionConcurrencyOutcome.APPLIED)));
    var results = contention.run();
    assertThat(results)
        .hasSize(12)
        .allSatisfy(
            r -> assertThat(r.status()).isEqualTo(AuthenticatedSessionLifecycleStatus.ACTIVE));
    assertThat(
            results.stream()
                .filter(r -> r.session().featureState().containsValue("concurrent-value")))
        .hasSize(10);
    assertThat(stored().featureState())
        .containsEntry("cache-a", "concurrent-value")
        .containsEntry("unrelated", "keep");
    assertThat(contention.applied.get()).isEqualTo(2);
    assertThat(contention.conflicts.get()).isEqualTo(31);
  }

  @ParameterizedTest
  @ValueSource(strings = {"expired", "missing", "corrupt", "load-error"})
  void exhaustedTouchNeverAuthenticatesAnUnusableOrUnreadableReload(String state) {
    seed();
    Mono<?> change =
        Mono.defer(
            () ->
                switch (state) {
                  case "expired" -> Mono.fromRunnable(() -> clock.now = clock.now.plusSeconds(121));
                  case "missing" -> redis.delete(key);
                  case "corrupt" ->
                      redis.opsForValue().set(key, "not-json", Duration.ofSeconds(40));
                  default ->
                      Mono.fromRunnable(
                          () ->
                              doReturn(
                                      Mono.error(
                                          new IllegalStateException("Synthetic reload failure")))
                                  .when(sessions)
                                  .load(any()));
                });
    var contention =
        new ContendedTouches(
            concurrency.updateIfUsable(ID, s -> s.withFeatureState("cache-a", "fresh"), "feature"),
            change);
    var results = contention.run();
    var expected =
        switch (state) {
          case "expired" -> AuthenticatedSessionLifecycleStatus.EXPIRED;
          case "missing" -> AuthenticatedSessionLifecycleStatus.NOT_FOUND;
          default -> AuthenticatedSessionLifecycleStatus.FAILED;
        };
    assertThat(results.stream().filter(r -> r.status() == expected)).hasSize(10);
    assertThat(results.stream().filter(r -> r.status() == expected))
        .allSatisfy(r -> assertThat(r.session()).isNull());
    assertThat(contention.applied.get()).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"touch", "logout", "revoke", "usable"})
  void exhaustionFallbackIsExclusiveToTouchAndDoesNotPerformAnotherWrite(String operation) {
    seed();
    var attempts = new AtomicInteger();
    var writer =
        new AuthenticatedSessionConcurrencyService(
            sessions,
            protector,
            command ->
                concurrency
                    .updateIfUsable(
                        ID,
                        s -> s.withFeatureState("cache-a", "winner-" + attempts.incrementAndGet()),
                        "winner")
                    .then(adapter.updateIfCurrent(command)),
            new SecureAuthenticatedSessionRevisionGenerator(),
            clock);
    var lifecycle =
        new AuthenticatedSessionLifecycleService(
            sessions,
            protector,
            () -> ID,
            new SecureAuthenticatedSessionRevisionGenerator(),
            writer,
            Duration.ofSeconds(120),
            Duration.ofSeconds(300),
            Duration.ofSeconds(20),
            clock);
    if (operation.equals("usable")) {
      var result =
          writer.updateIfUsable(ID, s -> s.withFeatureState("cache-a", "loser"), "usable").block();
      assertThat(result.outcome())
          .isEqualTo(AuthenticatedSessionConcurrencyOutcome.REVISION_CONFLICT_EXHAUSTED);
      assertThat(result.session()).isNull();
    } else {
      var result =
          switch (operation) {
            case "touch" ->
                lifecycle
                    .loadAndTouch(new LoadAndTouchAuthenticatedSessionCommand(ID, "touch"))
                    .block();
            case "logout" ->
                lifecycle.logout(new LogoutActiveAuthenticatedSessionCommand(ID, "logout")).block();
            default ->
                lifecycle
                    .revoke(new RevokeActiveAuthenticatedSessionCommand(ID, "test", "revoke"))
                    .block();
          };
      assertThat(result.status())
          .isEqualTo(
              operation.equals("touch")
                  ? AuthenticatedSessionLifecycleStatus.ACTIVE
                  : AuthenticatedSessionLifecycleStatus.FAILED);
      if (operation.equals("touch")) {
        assertThat(result.session().revision()).isEqualTo(stored().revision());
      } else {
        assertThat(result.session()).isNull();
      }
    }
    assertThat(attempts.get()).isEqualTo(3);
    assertThat(stored().status()).isEqualTo(AuthenticatedSessionStatus.ACTIVE);
    assertThat(stored().featureState())
        .containsEntry("cache-a", "winner-3")
        .containsEntry("unrelated", "keep");
    assertThat(commands).hasSize(3).allSatisfy(c -> assertThat(c.expiryDeadline()).isNull());
  }

  private final class ContendedTouches {
    private final List<Batch> batches = List.of(new Batch(12), new Batch(11), new Batch(10));
    private final Mono<?> beforeLastRound;
    private final Mono<?> afterLastRound;
    private final AtomicInteger applied = new AtomicInteger();
    private final AtomicInteger conflicts = new AtomicInteger();
    private int round;

    private ContendedTouches(Mono<?> beforeLastRound) {
      this(beforeLastRound, Mono.empty());
    }

    private ContendedTouches(Mono<?> beforeLastRound, Mono<?> afterLastRound) {
      this.beforeLastRound = beforeLastRound;
      this.afterLastRound = afterLastRound;
    }

    private Mono<AuthenticatedSessionCasUpdateResult> write(
        AuthenticatedSessionCasUpdateCommand command) {
      return Mono.defer(
          () -> {
            Batch batch;
            boolean lastArrival;
            boolean lastRound;
            synchronized (this) {
              batch = batches.get(round);
              lastRound = round == 2;
              lastArrival = batch.arrivals.incrementAndGet() == batch.size;
              if (lastArrival) {
                round++;
              }
            }
            Mono<?> ready =
                lastArrival
                    ? (lastRound ? beforeLastRound : Mono.empty())
                        .doOnSuccess(ignored -> batch.ready.tryEmitEmpty())
                    : batch.ready.asMono();
            return ready
                .then(adapter.updateIfCurrent(command))
                .flatMap(
                    result -> {
                      if (result.outcome() == AuthenticatedSessionCasUpdateOutcome.APPLIED) {
                        applied.incrementAndGet();
                      } else if (result.outcome()
                          == AuthenticatedSessionCasUpdateOutcome.REVISION_MISMATCH) {
                        conflicts.incrementAndGet();
                      }
                      Mono<?> finished =
                          batch.completions.incrementAndGet() == batch.size
                              ? (lastRound ? afterLastRound : Mono.empty())
                                  .doOnSuccess(ignored -> batch.finished.tryEmitEmpty())
                              : batch.finished.asMono();
                      return finished.then(batch.finished.asMono()).thenReturn(result);
                    });
          });
    }

    private List<AuthenticatedSessionLifecycleResult> run() {
      var writer =
          new AuthenticatedSessionConcurrencyService(
              sessions,
              protector,
              this::write,
              () -> new SessionRevision(String.format("%032x", revisions.incrementAndGet())),
              clock);
      var lifecycle =
          new AuthenticatedSessionLifecycleService(
              sessions,
              protector,
              () -> ID,
              new SecureAuthenticatedSessionRevisionGenerator(),
              writer,
              Duration.ofSeconds(120),
              Duration.ofSeconds(300),
              Duration.ofSeconds(20),
              clock);
      return Flux.range(0, 12)
          .flatMap(
              i ->
                  lifecycle.loadAndTouch(
                      new LoadAndTouchAuthenticatedSessionCommand(ID, "parallel")),
              12)
          .collectList()
          .block(Duration.ofSeconds(15));
    }
  }

  @Test
  void guardedWriteRejectsDeadlineCrossedAfterLoadWithoutChangingValueOrTtl() {
    var original = seed(copyTimes(session(), clock.instant().plusMillis(250),
        clock.instant().plusSeconds(300)));
    String before = redis.opsForValue().get(key).block();
    long deadline = expiry();
    beforeWrite.set(() -> Mono.fromRunnable(() -> awaitDeadline(original.idleExpiresAt()))
        .subscribeOn(Schedulers.boundedElastic()));
    var result = concurrency.updateIfUsableGuarded(ID,
        (s, now) -> true, (s, now) -> s.withFeatureState("grant", "never-written"), "test").block();
    assertThat(result.outcome()).isEqualTo(AuthenticatedSessionConcurrencyOutcome.SESSION_NOT_USABLE);
    assertThat(result.session()).isNull();
    assertThat(redis.opsForValue().get(key).block()).isEqualTo(before);
    assertThat(expiry()).isEqualTo(deadline);
    assertThat(commands).hasSize(1);
    assertThat(commands.getFirst().usableUntil()).isEqualTo(original.idleExpiresAt());
  }

  @Test
  void guardedRetryUsesNewDeadlineAfterConcurrentTouch() {
    var original = seed();
    beforeWrite.set(() -> touch());
    var result = concurrency.updateIfUsableGuarded(ID, (s, now) -> true,
        (s, now) -> s.withFeatureState("grant", "new"), "test").block();
    assertThat(result.outcome()).isEqualTo(AuthenticatedSessionConcurrencyOutcome.APPLIED);
    var guarded = commands.stream().filter(c -> c.usableUntil() != null).toList();
    assertThat(guarded).hasSize(2);
    assertThat(guarded.getFirst().usableUntil()).isEqualTo(original.idleExpiresAt());
    assertThat(guarded.getLast().usableUntil()).isEqualTo(stored().idleExpiresAt())
        .isAfter(original.idleExpiresAt());
    assertThat(stored().featureState()).containsEntry("grant", "new").containsEntry("unrelated", "keep");
    assertThat(expiry()).isEqualTo(stored().idleExpiresAt().toEpochMilli());
  }

  @Test
  void guardedTerminalRaceCannotRestoreGrant() {
    seed();
    beforeWrite.set(() -> terminal(AuthenticatedSessionStatus.REVOKED));
    var result = concurrency.updateIfUsableGuarded(ID, (s, now) -> true,
        (s, now) -> s.withFeatureState("grant", "never-written"), "test").block();
    assertThat(result.outcome()).isEqualTo(AuthenticatedSessionConcurrencyOutcome.SESSION_NOT_USABLE);
    assertThat(stored().status()).isEqualTo(AuthenticatedSessionStatus.REVOKED);
    assertThat(stored().featureState()).doesNotContainKey("grant");
    assertThat(outcomes).contains(AuthenticatedSessionCasUpdateOutcome.REVISION_MISMATCH);
  }

  @Test
  void usernameLimiterAdmitsTenAcrossParallelInvocationsAndIsolatesOtherUsers() {
    var limiter = new PasswordValidationThrottleService(
        new RedisRateLimitAdapter(redis, keys, clock), new byte[32], 10, 60);
    var actor = new ActorContext("synthetic-throttle-parallel", ActorUserRole.ROLE_USER_MBR);
    var results = Flux.range(0, 20).flatMap(i -> limiter.check(actor, "parallel"), 20)
        .collectList().block();
    assertThat(results.stream().filter(r -> r.status() ==
        PasswordValidationThrottleService.Status.ADMITTED)).hasSize(10);
    assertThat(results.stream().filter(r -> r.status() ==
        PasswordValidationThrottleService.Status.BLOCKED)).hasSize(10).allSatisfy(
            r -> assertThat(r.retryAfterSeconds()).isBetween(1, 60));
    assertThat(limiter.check(new ActorContext("synthetic-other-user", actor.actorUserRole()), "test")
        .block().status()).isEqualTo(PasswordValidationThrottleService.Status.ADMITTED);
  }

  @Test
  void usernameLimiterResetsAtItsConfiguredWindow() {
    var limiter = new PasswordValidationThrottleService(
        new RedisRateLimitAdapter(redis, keys, clock), new byte[32], 1, 1);
    var actor = new ActorContext("synthetic-throttle-reset", ActorUserRole.ROLE_USER_MBR);
    assertThat(limiter.check(actor, "first").block().status())
        .isEqualTo(PasswordValidationThrottleService.Status.ADMITTED);
    assertThat(limiter.check(actor, "second").block().status())
        .isEqualTo(PasswordValidationThrottleService.Status.BLOCKED);
    var next = Mono.delay(Duration.ofMillis(1100)).then(limiter.check(actor, "reset")).block();
    assertThat(next.status()).isEqualTo(PasswordValidationThrottleService.Status.ADMITTED);
  }

  @Test
  void guardedScriptRejectsInvalidDeadlinesBeforeAnyWrite() {
    seed();
    var script = new DefaultRedisScript<Long>();
    script.setLocation(new ClassPathResource("redis/authenticated-session/update-if-current-guarded.lua"));
    script.setResultType(Long.class);
    String before = redis.opsForValue().get(key).block();
    long deadline = expiry();
    for (String invalid : List.of("", "-1", "1.5", "bad", "9007199254740992", "0123")) {
      assertThat(redis.execute(script, List.of(key),
          List.of(INITIAL.value(), "never-written", invalid)).next().block()).isEqualTo(-4L);
      assertThat(redis.opsForValue().get(key).block()).isEqualTo(before);
      assertThat(expiry()).isEqualTo(deadline);
    }
  }

  @Test
  void guardedNoOpAndUnusableSessionNeverWrite() {
    seed();
    assertThat(concurrency.updateIfUsableGuarded(ID, (s, now) -> false,
        (s, now) -> { throw new AssertionError("ineligible mutation"); }, "test").block().outcome())
        .isEqualTo(AuthenticatedSessionConcurrencyOutcome.UNCHANGED);
    seed(expiredSession(false));
    assertThat(concurrency.updateIfUsableGuarded(ID, (s, now) -> true,
        (s, now) -> s, "test").block().outcome())
        .isEqualTo(AuthenticatedSessionConcurrencyOutcome.SESSION_NOT_USABLE);
    assertThat(commands).isEmpty();
  }

  private static final class Batch {
    private final int size;
    private final AtomicInteger arrivals = new AtomicInteger();
    private final AtomicInteger completions = new AtomicInteger();
    private final Sinks.Empty<Void> ready = Sinks.empty();
    private final Sinks.Empty<Void> finished = Sinks.empty();

    private Batch(int size) {
      this.size = size;
    }
  }

  @Test
  void actualExistingConsumersPreserveEachOthersFeatureEntriesAndExactRedisExpiry() {
    var original = seed();
    long deadline = expiry();
    var json = new JacksonTransientJsonCodec();
    var comparison =
        new FundComparisonOptionsCacheService(sessions, protector, concurrency, json, clock);
    comparison
        .write(
            ID,
            new FundComparisonOptionsCacheEntry(
                "synthetic-account", "synthetic-policy", "1M", List.of("TEST")),
            "comparison")
        .block();
    var afterComparison = stored().featureState();
    assertThat(afterComparison)
        .containsAllEntriesOf(original.featureState())
        .containsKey("fund-comparison.options");
    assertThat(expiry()).isEqualTo(deadline);
    var personal =
        new PersonalInformationConfigCacheService(sessions, protector, concurrency, json, clock);
    personal
        .write(
            ID,
            new PersonalInformationConfigCacheEntry(
                "synthetic-account", "synthetic-policy", Map.of("field", "editable")),
            "personal")
        .block();
    var afterPersonal = stored().featureState();
    assertThat(afterPersonal)
        .containsAllEntriesOf(afterComparison)
        .containsKey("personal-information.config");
    assertThat(expiry()).isEqualTo(deadline);
    var csrf =
        new AuthenticatedSessionCsrfRefreshService(
            sessions,
            protector,
            concurrency,
            () -> "synthetic-rotated-token",
            new Sha256CsrfTokenHasher(),
            clock);
    assertThat(csrf.refresh(ID.value(), "csrf").block().status().name()).isEqualTo("SUCCESS");
    assertThat(stored().featureState()).isEqualTo(afterPersonal);
    assertThat(stored().csrfTokenHash()).isNotEqualTo(original.csrfTokenHash());
    assertThat(stored().lastAccessedAt()).isEqualTo(original.lastAccessedAt());
    assertThat(stored().idleExpiresAt()).isEqualTo(original.idleExpiresAt());
    assertThat(expiry()).isEqualTo(deadline);
    assertThat(commands).hasSize(3).allSatisfy(c -> assertThat(c.expiryDeadline()).isNull());
  }

  @Test
  void missingLifecycleSessionNeverCreatesARecord() {
    assertThat(touch().block().status()).isEqualTo(AuthenticatedSessionLifecycleStatus.NOT_FOUND);
    assertThat(terminal(AuthenticatedSessionStatus.LOGGED_OUT).block().status())
        .isEqualTo(AuthenticatedSessionLifecycleStatus.NOT_FOUND);
    assertThat(redis.hasKey(key).block()).isFalse();
    assertThat(commands).isEmpty();
  }

  @Test
  void invalidDeadlineAndPersistentKeyCannotCausePartialReplacement() {
    seed();
    var script = new DefaultRedisScript<Long>();
    script.setLocation(
        new ClassPathResource("redis/authenticated-session/update-if-current-until.lua"));
    script.setResultType(Long.class);
    String before = redis.opsForValue().get(key).block();
    long deadline = expiry();
    for (String invalid :
        List.of(
            "",
            "-1",
            "1.5",
            "not-a-number",
            "9007199254740992",
            "0" + clock.instant().plusSeconds(90).toEpochMilli())) {
      assertThat(
              redis
                  .execute(script, List.of(key), List.of(INITIAL.value(), "never-written", invalid))
                  .next()
                  .block())
          .isEqualTo(-4L);
      assertThat(redis.opsForValue().get(key).block()).isEqualTo(before);
      assertThat(expiry()).isEqualTo(deadline);
    }
    redis.persist(key).block();
    assertThat(
            adapter
                .updateIfCurrent(command(session(), clock.instant().plusSeconds(90)))
                .block()
                .outcome())
        .isEqualTo(AuthenticatedSessionCasUpdateOutcome.CORRUPT_STATE);
    assertThat(redis.opsForValue().get(key).block()).isEqualTo(before);
  }

  private AuthenticatedSessionLifecycleService lifecycle() {
    return new AuthenticatedSessionLifecycleService(
        sessions,
        protector,
        () -> ID,
        () -> new SessionRevision(String.format("%032x", revisions.incrementAndGet())),
        concurrency,
        Duration.ofSeconds(120),
        Duration.ofSeconds(300),
        Duration.ofSeconds(20),
        clock);
  }

  private Mono<AuthenticatedSessionLifecycleResult> touch() {
    return lifecycle().loadAndTouch(new LoadAndTouchAuthenticatedSessionCommand(ID, "touch"));
  }

  private Mono<AuthenticatedSessionLifecycleResult> terminal(AuthenticatedSessionStatus target) {
    return switch (target) {
      case LOGGED_OUT ->
          lifecycle().logout(new LogoutActiveAuthenticatedSessionCommand(ID, "logout"));
      case REVOKED ->
          lifecycle().revoke(new RevokeActiveAuthenticatedSessionCommand(ID, "test", "revoke"));
      case EXPIRED -> touch();
      default -> throw new IllegalArgumentException("Expected terminal test case");
    };
  }

  private AuthenticatedSession expiredSession(boolean absolute) {
    var original = session();
    return copyTimes(
        original,
        absolute ? original.idleExpiresAt() : clock.instant().minusMillis(1),
        absolute ? clock.instant().minusMillis(1) : original.absoluteExpiresAt());
  }

  private AuthenticatedSession copyTimes(AuthenticatedSession s, Instant idle, Instant absolute) {
    return new AuthenticatedSession(
        s.authenticatedSessionId(),
        s.actorContext(),
        s.accounts(),
        s.status(),
        s.csrfTokenHash(),
        s.createdAt(),
        s.lastAccessedAt(),
        idle,
        absolute,
        s.revision(),
        s.fundPriceModeMetadata(),
        s.featureState());
  }

  private AuthenticatedSessionCasUpdateCommand command(
      AuthenticatedSession current, Instant deadline) {
    var replacement = new SessionRevision("b".repeat(32));
    return new AuthenticatedSessionCasUpdateCommand(
        ID, current.revision(), protector.protect(current), replacement, "test", deadline);
  }

  private AuthenticatedSession seed() {
    return seed(session());
  }

  private AuthenticatedSession seed(AuthenticatedSession session) {
    redis
        .opsForValue()
        .set(key, mapper.toJson(protector.protect(session)), Duration.ofSeconds(40))
        .block();
    return session;
  }

  private AuthenticatedSession session() {
    Instant now = clock.instant();
    return new AuthenticatedSession(
        ID,
        new ActorContext("synthetic-member", ActorUserRole.ROLE_USER_MBR),
        List.of(),
        AuthenticatedSessionStatus.ACTIVE,
        "synthetic-hash",
        now.minusSeconds(60),
        now.minusSeconds(10),
        now.plusSeconds(40),
        now.plusSeconds(300),
        INITIAL,
        null,
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
    return evaluate("return redis.call('PTTL', KEYS[1])");
  }

  private long evaluate(String lua) {
    return redis
        .execute(new DefaultRedisScript<Long>(lua, Long.class), List.of(key))
        .next()
        .block();
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
    private volatile Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
