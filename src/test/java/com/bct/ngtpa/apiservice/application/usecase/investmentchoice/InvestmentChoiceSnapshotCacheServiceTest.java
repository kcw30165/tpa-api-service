package com.bct.ngtpa.apiservice.application.usecase.investmentchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyOutcome;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyResult;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SuppressWarnings("unchecked")
class InvestmentChoiceSnapshotCacheServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-22T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final AuthenticatedSessionId SID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final String ACCOUNT_REF = "acc_abcdefghijklmnopqrstuvwx";
  private static final String OTHER_ACCOUNT_REF = "acc_otherotherotherotherother";
  private static final String POLICY_NO = "00000008802";
  private static final String KEY = "investment-choice.snapshot";
  private static final String PERSONAL_INFORMATION_KEY = "personal-information.config";

  private final AuthenticatedSessionPort sessions = mock(AuthenticatedSessionPort.class);
  private final AuthenticatedSessionProtectorPort protector =
      mock(AuthenticatedSessionProtectorPort.class);
  private final AuthenticatedSessionConcurrencyService concurrency =
      mock(AuthenticatedSessionConcurrencyService.class);
  private final TransientJsonPort json = new JacksonTransientJsonCodec();

  private InvestmentChoiceSnapshotCacheService service() {
    return new InvestmentChoiceSnapshotCacheService(sessions, protector, concurrency, json, CLOCK);
  }

  private static InvestmentChoiceSnapshot snapshot(String accountRef) {
    return new InvestmentChoiceSnapshot(
        accountRef,
        POLICY_NO,
        5,
        List.of(
            new InvestmentChoiceSnapshot.SwitchSource("BO", true),
            new InvestmentChoiceSnapshot.SwitchSource("EE", false)),
        List.of(
            new InvestmentChoiceSnapshot.Fund("FUND_A", true),
            new InvestmentChoiceSnapshot.Fund("FUND_B", false)));
  }

  private static String snapshotJson(int intervalPcnt) {
    return "{\"accountRef\":\""
        + ACCOUNT_REF
        + "\",\"policyNo\":\""
        + POLICY_NO
        + "\",\"intervalPcnt\":"
        + intervalPcnt
        + ",\"sources\":[],\"funds\":[]}";
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

  private void stored(AuthenticatedSession value) {
    when(sessions.load(any())).thenReturn(Mono.just(Optional.of(envelope())));
    when(protector.unprotect(any())).thenReturn(value);
  }

  private Optional<InvestmentChoiceSnapshot> read(String accountRef, String policyNo) {
    return service().read(SID, accountRef, policyNo, "req-1").block();
  }

  @Test
  void writesAndReadsEverySnapshotFieldIncludingUnavailableEntries() throws Exception {
    ArgumentCaptor<UnaryOperator<AuthenticatedSession>> mutator =
        ArgumentCaptor.forClass(UnaryOperator.class);
    when(concurrency.updateIfUsable(eq(SID), mutator.capture(), eq("req-1")))
        .thenReturn(
            Mono.just(AuthenticatedSessionConcurrencyResult.applied(usableSession(Map.of()))));

    StepVerifier.create(service().write(SID, snapshot(ACCOUNT_REF), "req-1")).verifyComplete();

    var updated = mutator.getValue().apply(usableSession(Map.of()));
    JsonNode wire = new ObjectMapper().readTree(updated.featureState().get(KEY));
    assertThat(wire.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("accountRef", "policyNo", "intervalPcnt", "sources", "funds");
    stored(updated);

    assertThat(read(ACCOUNT_REF, POLICY_NO)).contains(snapshot(ACCOUNT_REF));
    assertThat(wire.path("sources").get(1).path("available").asBoolean()).isFalse();
    assertThat(wire.path("funds").get(1).path("available").asBoolean()).isFalse();
    assertThat(wire.path("sources").get(0).fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("switchSource", "available");
    assertThat(wire.path("sources").get(1).fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("switchSource", "available");
    assertThat(wire.path("funds").get(0).fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("fundCode", "available");
    assertThat(wire.path("funds").get(1).fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("fundCode", "available");
  }

  @Test
  void overwritePreservesAnotherFeaturesEntryAndKeepsOnlyTheLatestAccountSnapshot() {
    ArgumentCaptor<UnaryOperator<AuthenticatedSession>> mutator =
        ArgumentCaptor.forClass(UnaryOperator.class);
    when(concurrency.updateIfUsable(eq(SID), mutator.capture(), eq("req-1")))
        .thenReturn(
            Mono.just(AuthenticatedSessionConcurrencyResult.applied(usableSession(Map.of()))));

    StepVerifier.create(service().write(SID, snapshot(ACCOUNT_REF), "req-1")).verifyComplete();
    StepVerifier.create(service().write(SID, snapshot(OTHER_ACCOUNT_REF), "req-1"))
        .verifyComplete();

    AuthenticatedSession afterFirst =
        mutator
            .getAllValues()
            .getFirst()
            .apply(usableSession(Map.of(PERSONAL_INFORMATION_KEY, "keep")));
    AuthenticatedSession afterSecond = mutator.getAllValues().get(1).apply(afterFirst);
    assertThat(afterFirst.featureState())
        .containsEntry(PERSONAL_INFORMATION_KEY, "keep")
        .containsKey(KEY);
    assertThat(afterSecond.featureState())
        .containsEntry(PERSONAL_INFORMATION_KEY, "keep")
        .containsKey(KEY);
    stored(afterSecond);

    assertThat(read(OTHER_ACCOUNT_REF, POLICY_NO)).contains(snapshot(OTHER_ACCOUNT_REF));
    assertThat(read(ACCOUNT_REF, POLICY_NO)).isEmpty();
  }

  @Test
  void readMissesWhenNoSnapshotIsStored() {
    stored(usableSession(Map.of(PERSONAL_INFORMATION_KEY, "keep")));

    assertThat(read(ACCOUNT_REF, POLICY_NO)).isEmpty();
  }

  @Test
  void readMissesWhenStoredValueCannotBeDecoded() {
    stored(usableSession(Map.of(KEY, "not-json")));

    assertThat(read(ACCOUNT_REF, POLICY_NO)).isEmpty();
  }

  @Test
  void readMissesWhenStoredValueHasAnUnknownProperty() {
    stored(
        usableSession(
            Map.of(
                KEY,
                "{\"accountRef\":\""
                    + ACCOUNT_REF
                    + "\",\"policyNo\":\""
                    + POLICY_NO
                    + "\",\"intervalPcnt\":5,\"sources\":[],\"funds\":[],\"unexpected\":true}")));

    assertThat(read(ACCOUNT_REF, POLICY_NO)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 101})
  void readMissesWhenStoredSnapshotIntervalIsOutOfRange(int intervalPcnt) {
    stored(usableSession(Map.of(KEY, snapshotJson(intervalPcnt))));

    StepVerifier.create(service().read(SID, ACCOUNT_REF, POLICY_NO, "req-1"))
        .assertNext(result -> assertThat(result).isEmpty())
        .verifyComplete();
  }

  @Test
  void readMissesForADifferentAccountRefOrPolicyNo() {
    stored(usableSession(Map.of(KEY, json.write(snapshot(ACCOUNT_REF)))));

    assertThat(read(OTHER_ACCOUNT_REF, POLICY_NO)).isEmpty();
    assertThat(read(ACCOUNT_REF, "00000009999")).isEmpty();
  }

  @Test
  void readMissesWhenSessionIsExpiredOrLoggedOut() {
    stored(
        session(
            AuthenticatedSessionStatus.ACTIVE,
            Map.of(KEY, json.write(snapshot(ACCOUNT_REF))),
            NOW.minusSeconds(1)));
    assertThat(read(ACCOUNT_REF, POLICY_NO)).isEmpty();

    stored(
        session(
            AuthenticatedSessionStatus.REVOKED,
            Map.of(KEY, json.write(snapshot(ACCOUNT_REF))),
            NOW.plusSeconds(900)));
    assertThat(read(ACCOUNT_REF, POLICY_NO)).isEmpty();
  }

  @Test
  void readMissesWhenSessionLoadingFails() {
    when(sessions.load(any())).thenReturn(Mono.error(new IllegalStateException("redis down")));

    StepVerifier.create(service().read(SID, ACCOUNT_REF, POLICY_NO, "req-1"))
        .assertNext(result -> assertThat(result).isEmpty())
        .verifyComplete();
  }

  @Test
  void readMissesWhenSessionCannotBeUnprotected() {
    when(sessions.load(any())).thenReturn(Mono.just(Optional.of(envelope())));
    when(protector.unprotect(any())).thenThrow(new IllegalStateException("unreadable"));

    assertThat(read(ACCOUNT_REF, POLICY_NO)).isEmpty();
  }

  @Test
  void writeCompletesNormallyWhenTheSessionIsNotUsable() {
    when(concurrency.updateIfUsable(any(), any(), any()))
        .thenReturn(
            Mono.just(
                AuthenticatedSessionConcurrencyResult.of(
                    AuthenticatedSessionConcurrencyOutcome.SESSION_NOT_USABLE)));

    StepVerifier.create(service().write(SID, snapshot(ACCOUNT_REF), "req-1")).verifyComplete();
  }

  @Test
  void writeCompletesNormallyWhenUpdateFails() {
    when(concurrency.updateIfUsable(any(), any(), any()))
        .thenReturn(Mono.error(new IllegalStateException("redis down")));

    StepVerifier.create(service().write(SID, snapshot(ACCOUNT_REF), "req-1")).verifyComplete();
  }

  @Test
  void writeCompletesNormallyWhenEncodingFails() {
    TransientJsonPort failingJson = mock(TransientJsonPort.class);
    when(failingJson.write(any())).thenThrow(new IllegalArgumentException("encoding failed"));
    var failingService =
        new InvestmentChoiceSnapshotCacheService(
            sessions, protector, concurrency, failingJson, CLOCK);

    StepVerifier.create(failingService.write(SID, snapshot(ACCOUNT_REF), "req-1")).verifyComplete();
    verify(concurrency, never()).updateIfUsable(any(), any(), any());
  }

  @Test
  void operationalFailuresDoNotPublishFailureEventsOrEscape() {
    boolean publishesFailureEvents =
        Stream.concat(
                Arrays.stream(InvestmentChoiceSnapshotCacheService.class.getDeclaredFields())
                    .map(java.lang.reflect.Field::getType),
                Arrays.stream(InvestmentChoiceSnapshotCacheService.class.getDeclaredConstructors())
                    .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes())))
            .anyMatch(FailureEventPort.class::isAssignableFrom);
    assertThat(publishesFailureEvents).isFalse();

    when(sessions.load(any())).thenReturn(Mono.error(new IllegalStateException("redis down")));
    StepVerifier.create(service().read(SID, ACCOUNT_REF, POLICY_NO, "req-1"))
        .assertNext(result -> assertThat(result).isEmpty())
        .verifyComplete();

    when(concurrency.updateIfUsable(any(), any(), any()))
        .thenReturn(Mono.error(new IllegalStateException("redis down")));
    StepVerifier.create(service().write(SID, snapshot(ACCOUNT_REF), "req-1")).verifyComplete();
  }

  @Test
  void requiresItsArgumentsAndDependencies() {
    var readService = service();
    var sessionWriteService = service();
    var snapshotWriteService = service();
    var snapshot = snapshot(ACCOUNT_REF);

    assertThatThrownBy(() -> readService.read(null, ACCOUNT_REF, POLICY_NO, "req-1"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> sessionWriteService.write(null, snapshot, "req-1"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> snapshotWriteService.write(SID, null, "req-1"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new InvestmentChoiceSnapshotCacheService(null, protector, concurrency, json, CLOCK))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new InvestmentChoiceSnapshotCacheService(sessions, null, concurrency, json, CLOCK))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> new InvestmentChoiceSnapshotCacheService(sessions, protector, null, json, CLOCK))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new InvestmentChoiceSnapshotCacheService(
                    sessions, protector, concurrency, null, CLOCK))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new InvestmentChoiceSnapshotCacheService(
                    sessions, protector, concurrency, json, null))
        .isInstanceOf(NullPointerException.class);
  }
}
