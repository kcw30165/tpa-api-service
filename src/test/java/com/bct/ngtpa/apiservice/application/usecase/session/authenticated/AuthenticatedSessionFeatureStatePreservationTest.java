package com.bct.ngtpa.apiservice.application.usecase.session.authenticated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceModeData;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedAccountRef;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionAccount;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionCasUpdateOutcome;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionCasUpdateResult;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionPersistenceResult;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionPersistenceStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.ProtectedAuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.SessionRevision;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionCasUpdatePort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.csrf.AuthenticatedSessionCsrfRefreshService;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.replay.RecoverCommittedLoginService;
import com.bct.ngtpa.apiservice.application.usecase.fundprice.FundPriceModeUseCase;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * Fund Comparison M5.1 regression guard: every production site that rebuilds an
 * AuthenticatedSession must carry featureState through unchanged (09 section 9.5.1). The three
 * private static mutator factories are reached by reflection because their only public entry
 * points need far wider fixtures than the property under test.
 */
@SuppressWarnings({"unchecked", "java:S3011"})
class AuthenticatedSessionFeatureStatePreservationTest {

  private static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");
  private static final AuthenticatedSessionId ID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final Map<String, String> FEATURE_STATE =
      Map.of("fund-comparison.options", "encoded-entry");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private static AuthenticatedSession session(Map<String, String> featureState) {
    return new AuthenticatedSession(
        ID,
        new ActorContext("member-1", ActorUserRole.ROLE_USER_MBR),
        List.of(),
        AuthenticatedSessionStatus.ACTIVE,
        "hash",
        NOW.minusSeconds(60),
        NOW.minusSeconds(60),
        NOW.plusSeconds(900),
        NOW.plusSeconds(28800),
        new SessionRevision("a".repeat(32)),
        null,
        featureState);
  }

  private static ProtectedAuthenticatedSession envelope(AuthenticatedSession session) {
    return new ProtectedAuthenticatedSession(
        session.authenticatedSessionId().value(),
        session.status(),
        new ProtectedAuthenticatedSession.Data(
            Map.of(
                ProtectedAuthenticatedSession.CIPHERTEXT_ATTRIBUTE, "c",
                ProtectedAuthenticatedSession.ALGORITHM_ATTRIBUTE, "AES/GCM/NoPadding",
                ProtectedAuthenticatedSession.NONCE_ATTRIBUTE, "n",
                ProtectedAuthenticatedSession.AUTHENTICATION_TAG, "t",
                ProtectedAuthenticatedSession.KEY_VERSION, "v1")),
        session.createdAt(),
        session.lastAccessedAt(),
        session.idleExpiresAt(),
        session.absoluteExpiresAt(),
        session.revision());
  }

  private static AuthenticatedSessionProtectorPort protectorReturning(AuthenticatedSession session) {
    AuthenticatedSessionProtectorPort protector = mock(AuthenticatedSessionProtectorPort.class);
    when(protector.unprotect(any())).thenReturn(session);
    when(protector.protect(any()))
        .thenAnswer(invocation -> envelope(invocation.getArgument(0)));
    return protector;
  }

  private static UnaryOperator<AuthenticatedSession> staticMutator(
      Class<?> owner, String name, Class<?> parameterType, Object argument) throws Exception {
    Method method = owner.getDeclaredMethod(name, parameterType);
    method.setAccessible(true);
    return (UnaryOperator<AuthenticatedSession>) method.invoke(null, argument);
  }

  @Test
  void concurrencyServiceReplacementKeepsFeatureState() {
    var current = session(FEATURE_STATE);
    AuthenticatedSessionPort sessions = mock(AuthenticatedSessionPort.class);
    when(sessions.load(any())).thenReturn(Mono.just(Optional.of(envelope(current))));
    AuthenticatedSessionCasUpdatePort casPort = mock(AuthenticatedSessionCasUpdatePort.class);
    when(casPort.updateIfCurrent(any()))
        .thenReturn(
            Mono.just(
                AuthenticatedSessionCasUpdateResult.of(
                    AuthenticatedSessionCasUpdateOutcome.APPLIED)));
    var service =
        new AuthenticatedSessionConcurrencyService(
            sessions,
            protectorReturning(current),
            casPort,
            () -> new SessionRevision("b".repeat(32)),
            CLOCK);

    var result = service.updateIfUsable(ID, UnaryOperator.identity(), "req-1").block();

    assertThat(result.outcome()).isEqualTo(AuthenticatedSessionConcurrencyOutcome.APPLIED);
    assertThat(result.session().featureState()).isEqualTo(FEATURE_STATE);
  }

  @Test
  void lifecycleLoadAndTouchKeepsFeatureState() {
    var current = session(FEATURE_STATE);
    AuthenticatedSessionPort port = mock(AuthenticatedSessionPort.class);
    when(port.load(any())).thenReturn(Mono.just(Optional.of(envelope(current))));
    when(port.touch(any()))
        .thenReturn(
            Mono.just(
                new AuthenticatedSessionPersistenceResult(
                    AuthenticatedSessionPersistenceStatus.TOUCHED)));
    var lifecycle =
        new AuthenticatedSessionLifecycleService(
            port,
            protectorReturning(current),
            () -> ID,
            Duration.ofMinutes(15),
            Duration.ofHours(8),
            Duration.ofMinutes(5),
            CLOCK);

    var result =
        lifecycle.loadAndTouch(new LoadAndTouchAuthenticatedSessionCommand(ID, "req-1")).block();

    assertThat(result.status()).isEqualTo(AuthenticatedSessionLifecycleStatus.ACTIVE);
    assertThat(result.session().featureState()).isEqualTo(FEATURE_STATE);
  }

  @Test
  void lifecycleCreateStartsWithEmptyFeatureState() {
    AuthenticatedSessionPort port = mock(AuthenticatedSessionPort.class);
    when(port.store(any()))
        .thenReturn(
            Mono.just(
                new AuthenticatedSessionPersistenceResult(
                    AuthenticatedSessionPersistenceStatus.STORED)));
    var lifecycle =
        new AuthenticatedSessionLifecycleService(
            port,
            protectorReturning(session(Map.of())),
            () -> ID,
            Duration.ofMinutes(15),
            Duration.ofHours(8),
            Duration.ofMinutes(5),
            CLOCK);
    var account =
        new AuthenticatedSessionAccount(
            new AuthenticatedAccountRef("acc_abcdefghijklmnopqrstuvwx"),
            "DB",
            "00000008802",
            "0001",
            "DBA",
            "OE",
            null,
            null);

    var result =
        lifecycle
            .create(
                new CreateAuthenticatedSessionCommand(
                    new ActorContext("member-1", ActorUserRole.ROLE_USER_MBR),
                    List.of(account),
                    "hash",
                    "req-1"))
            .block();

    assertThat(result.status()).isEqualTo(AuthenticatedSessionLifecycleStatus.ACTIVE);
    assertThat(result.session().featureState()).isEmpty();
  }

  @Test
  void csrfRefreshMutatorKeepsFeatureState() throws Exception {
    var mutator =
        staticMutator(
            AuthenticatedSessionCsrfRefreshService.class, "withCsrfHash", String.class, "new");

    var updated = mutator.apply(session(FEATURE_STATE));

    assertThat(updated.csrfTokenHash()).isEqualTo("new");
    assertThat(updated.featureState()).isEqualTo(FEATURE_STATE);
  }

  @Test
  void committedLoginRecoveryMutatorKeepsFeatureState() throws Exception {
    var mutator =
        staticMutator(RecoverCommittedLoginService.class, "withCsrfHash", String.class, "new");

    var updated = mutator.apply(session(FEATURE_STATE));

    assertThat(updated.csrfTokenHash()).isEqualTo("new");
    assertThat(updated.featureState()).isEqualTo(FEATURE_STATE);
  }

  @Test
  void fundPriceModeMutatorKeepsFeatureState() throws Exception {
    var raw =
        new RawFundPriceModeData(
            "DAILY", "BIDOFFER PRICE", List.of(LocalDate.of(2026, 10, 20)), 36, "5");
    var mutator =
        staticMutator(FundPriceModeUseCase.class, "stampMetadata", RawFundPriceModeData.class, raw);

    var updated = mutator.apply(session(FEATURE_STATE));

    assertThat(updated.fundPriceModeMetadata().mode()).isEqualTo("DAILY");
    assertThat(updated.featureState()).isEqualTo(FEATURE_STATE);
  }
}
