package com.bct.ngtpa.apiservice.application.usecase.investmentchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSnapshot;
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

class InvestmentChoiceSnapshotCacheServiceLoggingTest {

  private static final Instant NOW = Instant.parse("2026-10-22T00:00:00Z");
  private static final AuthenticatedSessionId SID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final String ACCOUNT_REF = "acc_sensitive_marker";
  private static final String POLICY_NO = "policy_sensitive_marker";
  private static final String KEY = "investment-choice.snapshot";

  private final Logger logger =
      (Logger) LoggerFactory.getLogger(InvestmentChoiceSnapshotCacheService.class);
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

  private InvestmentChoiceSnapshotCacheService service() {
    return new InvestmentChoiceSnapshotCacheService(
        sessions,
        protector,
        concurrency,
        new JacksonTransientJsonCodec(),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static InvestmentChoiceSnapshot snapshot() {
    return new InvestmentChoiceSnapshot(
        ACCOUNT_REF,
        POLICY_NO,
        5,
        List.of(
            new InvestmentChoiceSnapshot.SwitchSource("BO", true),
            new InvestmentChoiceSnapshot.SwitchSource("EE", false)),
        List.of(
            new InvestmentChoiceSnapshot.Fund("FUND_A", true),
            new InvestmentChoiceSnapshot.Fund("FUND_B", false)));
  }

  private static AuthenticatedSession session(
      AuthenticatedSessionStatus status, Map<String, String> featureState, Instant idleExpiresAt) {
    return new AuthenticatedSession(
        SID,
        new ActorContext("member-1", ActorUserRole.ROLE_USER_MBR),
        List.of(),
        status,
        "hash",
        NOW.minusSeconds(60),
        NOW.minusSeconds(60),
        idleExpiresAt,
        NOW.plusSeconds(28800),
        new SessionRevision("a".repeat(32)),
        null,
        featureState);
  }

  private static AuthenticatedSession usableSession(Map<String, String> featureState) {
    return session(AuthenticatedSessionStatus.ACTIVE, featureState, NOW.plusSeconds(900));
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

  private List<ILoggingEvent> events() {
    return appender.list;
  }

  @Test
  void everyCachePathLogsOnlyRequestIdAndFixedReasonCodes() {
    when(sessions.load(any())).thenReturn(Mono.just(Optional.empty()));
    service().read(SID, ACCOUNT_REF, POLICY_NO, "req-1").block();

    when(sessions.load(any())).thenReturn(Mono.error(new IllegalStateException("backend detail")));
    service().read(SID, ACCOUNT_REF, POLICY_NO, "req-2").block();

    when(sessions.load(any())).thenReturn(Mono.just(Optional.of(envelope())));
    when(protector.unprotect(any())).thenReturn(usableSession(Map.of()));
    service().read(SID, ACCOUNT_REF, POLICY_NO, "req-3").block();

    when(protector.unprotect(any())).thenReturn(usableSession(Map.of(KEY, "not-json")));
    service().read(SID, ACCOUNT_REF, POLICY_NO, "req-4").block();

    String unknownProperty =
        "{\"accountRef\":\""
            + ACCOUNT_REF
            + "\",\"policyNo\":\""
            + POLICY_NO
            + "\",\"intervalPcnt\":5,\"sources\":[],\"funds\":[],\"unexpected\":true}";
    when(protector.unprotect(any())).thenReturn(usableSession(Map.of(KEY, unknownProperty)));
    service().read(SID, ACCOUNT_REF, POLICY_NO, "req-5").block();

    String encoded = new JacksonTransientJsonCodec().write(snapshot());
    doReturn(usableSession(Map.of(KEY, encoded))).when(protector).unprotect(any());
    service().read(SID, "acc_other_marker", POLICY_NO, "req-6").block();

    when(protector.unprotect(any()))
        .thenReturn(
            session(AuthenticatedSessionStatus.ACTIVE, Map.of(KEY, encoded), NOW.minusSeconds(1)));
    service().read(SID, ACCOUNT_REF, POLICY_NO, "req-7").block();

    when(protector.unprotect(any()))
        .thenReturn(
            session(
                AuthenticatedSessionStatus.REVOKED, Map.of(KEY, encoded), NOW.plusSeconds(900)));
    service().read(SID, ACCOUNT_REF, POLICY_NO, "req-8").block();

    when(protector.unprotect(any())).thenThrow(new IllegalStateException("decrypt detail"));
    service().read(SID, ACCOUNT_REF, POLICY_NO, "req-9").block();

    doReturn(usableSession(Map.of(KEY, encoded))).when(protector).unprotect(any());
    service().read(SID, ACCOUNT_REF, POLICY_NO, "req-10").block();

    when(concurrency.updateIfUsable(any(), any(), any()))
        .thenReturn(
            Mono.just(
                AuthenticatedSessionConcurrencyResult.of(
                    AuthenticatedSessionConcurrencyOutcome.SESSION_NOT_USABLE)));
    service().write(SID, snapshot(), "req-11").block();

    when(concurrency.updateIfUsable(any(), any(), any()))
        .thenReturn(Mono.error(new IllegalStateException("backend detail")));
    service().write(SID, snapshot(), "req-12").block();

    var failingJson = mock(com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort.class);
    when(failingJson.write(any())).thenThrow(new IllegalStateException("encoding detail"));
    new InvestmentChoiceSnapshotCacheService(
            sessions, protector, concurrency, failingJson, Clock.fixed(NOW, ZoneOffset.UTC))
        .write(SID, snapshot(), "req-13")
        .block();

    when(concurrency.updateIfUsable(any(), any(), any()))
        .thenReturn(
            Mono.just(AuthenticatedSessionConcurrencyResult.applied(usableSession(Map.of()))));
    service().write(SID, snapshot(), "req-14").block();

    assertThat(events()).hasSize(14);
    assertThat(events()).extracting(ILoggingEvent::getLevel).containsOnly(Level.INFO, Level.WARN);
    assertThat(events())
        .extracting(ILoggingEvent::getFormattedMessage)
        .allSatisfy(
            message ->
                assertThat(message)
                    .contains("requestId=")
                    .doesNotContain(
                        ACCOUNT_REF,
                        POLICY_NO,
                        SID.value(),
                        "FUND_A",
                        "FUND_B",
                        "BO",
                        "EE",
                        "backend detail",
                        "decrypt detail",
                        "encoding detail",
                        "IllegalStateException"));
    assertThat(events())
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(message -> message.contains("reason=session-missing"))
        .anyMatch(message -> message.contains("reason=failed"))
        .anyMatch(message -> message.contains("reason=absent"))
        .anyMatch(message -> message.contains("reason=unreadable"))
        .anyMatch(message -> message.contains("reason=session-unreadable"))
        .anyMatch(message -> message.contains("reason=session-unusable"))
        .anyMatch(message -> message.contains("reason=account-mismatch"))
        .anyMatch(message -> message.contains("reason=session-not-usable"));
  }
}
