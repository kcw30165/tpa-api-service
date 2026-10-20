package com.bct.ngtpa.apiservice.application.usecase.fundcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonOptionsCacheEntry;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.ProtectedAuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.SessionRevision;
import com.bct.ngtpa.apiservice.application.idempotency.JacksonTransientJsonCodec;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyOutcome;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyResult;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/** Fund Comparison M5.2: cache events are logged with reasons and never expose account data. */
class FundComparisonOptionsCacheServiceLoggingTest {

  private static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");
  private static final AuthenticatedSessionId SID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final String ACCOUNT_REF = "acc_secretsecretsecretsecret";
  private static final String POLICY_NO = "00000008802";

  private final Logger logger =
      (Logger) LoggerFactory.getLogger(FundComparisonOptionsCacheService.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private Level previousLevel;

  private final AuthenticatedSessionPort sessions = mock(AuthenticatedSessionPort.class);
  private final AuthenticatedSessionProtectorPort protector =
      mock(AuthenticatedSessionProtectorPort.class);
  private final AuthenticatedSessionConcurrencyService concurrency =
      mock(AuthenticatedSessionConcurrencyService.class);

  @BeforeEach
  void attach() {
    previousLevel = logger.getLevel();
    logger.setLevel(Level.INFO);
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void detach() {
    logger.detachAppender(appender);
    logger.setLevel(previousLevel);
  }

  private FundComparisonOptionsCacheService service() {
    return new FundComparisonOptionsCacheService(
        sessions,
        protector,
        concurrency,
        new JacksonTransientJsonCodec(),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static AuthenticatedSession session(Map<String, String> featureState) {
    return new AuthenticatedSession(
        SID,
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

  private static ProtectedAuthenticatedSession envelope() {
    return new ProtectedAuthenticatedSession(
        SID.value(),
        AuthenticatedSessionStatus.ACTIVE,
        new ProtectedAuthenticatedSession.Data(
            Map.of(
                ProtectedAuthenticatedSession.CIPHERTEXT_ATTRIBUTE, "c",
                ProtectedAuthenticatedSession.ALGORITHM_ATTRIBUTE, "AES/GCM/NoPadding",
                ProtectedAuthenticatedSession.NONCE_ATTRIBUTE, "n",
                ProtectedAuthenticatedSession.AUTHENTICATION_TAG, "t",
                ProtectedAuthenticatedSession.KEY_VERSION, "v1")),
        NOW.minusSeconds(60),
        NOW.minusSeconds(60),
        NOW.plusSeconds(900),
        NOW.plusSeconds(28800),
        new SessionRevision("a".repeat(32)));
  }

  private List<String> messages() {
    return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  @Test
  void hitMissAndSkippedWriteAreLoggedWithoutAccountData() {
    var entry = new FundComparisonOptionsCacheEntry(ACCOUNT_REF, POLICY_NO, "10D", List.of("FAEU"));
    var stored =
        "{\"accountRef\":\""
            + ACCOUNT_REF
            + "\",\"policyNo\":\""
            + POLICY_NO
            + "\",\"rawTimeRange\":\"10D\",\"fundCodes\":[\"FAEU\"]}";
    when(sessions.load(any())).thenReturn(Mono.just(Optional.of(envelope())));
    when(protector.unprotect(any())).thenReturn(session(Map.of("fund-comparison.options", stored)));
    when(concurrency.updateIfUsable(any(), any(), any()))
        .thenReturn(
            Mono.just(
                AuthenticatedSessionConcurrencyResult.of(
                    AuthenticatedSessionConcurrencyOutcome.REVISION_CONFLICT_EXHAUSTED)));

    service().read(SID, ACCOUNT_REF, POLICY_NO, "req-1").block();
    service().read(SID, "acc_otherotherotherotherother", POLICY_NO, "req-2").block();
    service().write(SID, entry, "req-3").block();

    assertThat(messages())
        .anyMatch(m -> m.contains("event=fund-comparison.options-cache.hit"))
        .anyMatch(m -> m.contains("reason=account-mismatch"))
        .anyMatch(m -> m.contains("event=fund-comparison.options-cache.write.skipped"))
        .anyMatch(m -> m.contains("reason=conflict"))
        .allSatisfy(m -> assertThat(m).doesNotContain(ACCOUNT_REF, POLICY_NO));
  }
}
