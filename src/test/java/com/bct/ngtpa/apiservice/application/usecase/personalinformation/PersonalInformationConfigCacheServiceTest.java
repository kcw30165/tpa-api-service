package com.bct.ngtpa.apiservice.application.usecase.personalinformation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.PersonalInformationConfigCacheEntry;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.ProtectedAuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.SessionRevision;
import com.bct.ngtpa.apiservice.application.idempotency.JacksonTransientJsonCodec;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyOutcome;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyResult;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Session cache read and write for the field-access config (design section 5, D-11 to D-13). */
@SuppressWarnings("unchecked")
class PersonalInformationConfigCacheServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-22T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final AuthenticatedSessionId SID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final String ACCOUNT_REF = "acc_abcdefghijklmnopqrstuvwx";
  private static final String POLICY_NO = "00000008802";
  private static final String KEY = "personal-information.config";
  private static final String STORED =
      "{\"accountRef\":\"acc_abcdefghijklmnopqrstuvwx\",\"policyNo\":\"00000008802\","
          + "\"itemConfig\":{\"addr1\":\"EDITABLE_COM\",\"email\":\"READONLY\"}}";

  private final AuthenticatedSessionPort sessions = mock(AuthenticatedSessionPort.class);
  private final AuthenticatedSessionProtectorPort protector =
      mock(AuthenticatedSessionProtectorPort.class);
  private final AuthenticatedSessionConcurrencyService concurrency =
      mock(AuthenticatedSessionConcurrencyService.class);
  private final TransientJsonPort json = new JacksonTransientJsonCodec();

  private PersonalInformationConfigCacheService service() {
    return new PersonalInformationConfigCacheService(sessions, protector, concurrency, json, CLOCK);
  }

  private static PersonalInformationConfigCacheEntry entry() {
    return new PersonalInformationConfigCacheEntry(
        ACCOUNT_REF, POLICY_NO, Map.of("addr1", "EDITABLE_COM", "email", "READONLY"));
  }

  private static AuthenticatedSession session(
      Map<String, String> featureState, Instant lastAccessedAt, Instant idleExpiresAt) {
    return new AuthenticatedSession(
        SID,
        new ActorContext("member-1", ActorUserRole.ROLE_USER_MBR),
        List.of(),
        AuthenticatedSessionStatus.ACTIVE,
        "hash",
        lastAccessedAt,
        lastAccessedAt,
        idleExpiresAt,
        NOW.plusSeconds(28800),
        new SessionRevision("a".repeat(32)),
        null,
        featureState);
  }

  private static AuthenticatedSession usableSession(Map<String, String> featureState) {
    return session(featureState, NOW.minusSeconds(60), NOW.plusSeconds(900));
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

  private void stored(AuthenticatedSession session) {
    when(sessions.load(any())).thenReturn(Mono.just(Optional.of(envelope())));
    when(protector.unprotect(any())).thenReturn(session);
  }

  private Optional<PersonalInformationConfigCacheEntry> read(String accountRef, String policyNo) {
    return service().read(SID, accountRef, policyNo, "req-1").block();
  }

  // ----- read ---------------------------------------------------------------

  @Test
  void readReturnsTheEntryOnAHit() {
    stored(usableSession(Map.of(KEY, STORED)));

    assertThat(read(ACCOUNT_REF, POLICY_NO)).contains(entry());
    verifyNoInteractions(concurrency);
    verify(sessions, never()).touch(any());
  }

  @Test
  void readTreatsAStoredNullConfigValueAsBlank() {
    stored(
        usableSession(
            Map.of(
                KEY,
                "{\"accountRef\":\"acc_abcdefghijklmnopqrstuvwx\",\"policyNo\":\"00000008802\","
                    + "\"itemConfig\":{\"addr1\":null,\"email\":\"READONLY\"}}")));

    var result = read(ACCOUNT_REF, POLICY_NO);

    assertThat(result).isPresent();
    assertThat(result.get().itemConfig())
        .containsEntry("addr1", "")
        .containsEntry("email", "READONLY");
  }

  @Test
  void readIsAMissWhenTheSessionIsMissing() {
    when(sessions.load(any())).thenReturn(Mono.just(Optional.empty()));

    assertThat(read(ACCOUNT_REF, POLICY_NO)).isEmpty();
    verifyNoInteractions(protector);
  }

  @Test
  void readIsAMissWhenTheSessionCannotBeDecrypted() {
    when(sessions.load(any())).thenReturn(Mono.just(Optional.of(envelope())));
    when(protector.unprotect(any())).thenThrow(new IllegalStateException("bad"));

    assertThat(read(ACCOUNT_REF, POLICY_NO)).isEmpty();
  }

  @Test
  void readIsAMissWhenTheSessionIsNotUsable() {
    stored(session(Map.of(KEY, STORED), NOW.minusSeconds(120), NOW.minusSeconds(60)));

    assertThat(read(ACCOUNT_REF, POLICY_NO)).isEmpty();
  }

  @Test
  void readIsAMissWhenNoEntryIsStored() {
    stored(usableSession(Map.of("other.feature", "x")));

    assertThat(read(ACCOUNT_REF, POLICY_NO)).isEmpty();
  }

  @Test
  void readIsAMissWhenTheStoredValueIsNotJson() {
    stored(usableSession(Map.of(KEY, "not-json")));

    assertThat(read(ACCOUNT_REF, POLICY_NO)).isEmpty();
  }

  @Test
  void readIsAMissWhenTheStoredValueHasAnUnknownShape() {
    stored(usableSession(Map.of(KEY, "{\"unexpected\":true}")));

    assertThat(read(ACCOUNT_REF, POLICY_NO)).isEmpty();
  }

  @Test
  void readIsAMissWhenTheStoredValueFailsEntryValidation() {
    stored(
        usableSession(
            Map.of(
                KEY,
                "{\"accountRef\":\"acc_abcdefghijklmnopqrstuvwx\",\"policyNo\":\"00000008802\","
                    + "\"itemConfig\":{}}")));

    assertThat(read(ACCOUNT_REF, POLICY_NO)).isEmpty();
  }

  @Test
  void readIsAMissForADifferentAccountRef() {
    stored(usableSession(Map.of(KEY, STORED)));

    assertThat(read("acc_otherotherotherotherother", POLICY_NO)).isEmpty();
  }

  @Test
  void readIsAMissForADifferentPolicyNo() {
    stored(usableSession(Map.of(KEY, STORED)));

    assertThat(read(ACCOUNT_REF, "00000009999")).isEmpty();
  }

  @Test
  void readIsAMissWhenLoadingFails() {
    when(sessions.load(any())).thenReturn(Mono.error(new IllegalStateException("redis down")));

    StepVerifier.create(service().read(SID, ACCOUNT_REF, POLICY_NO, "req-1"))
        .assertNext(result -> assertThat(result).isEmpty())
        .verifyComplete();
  }

  // ----- write --------------------------------------------------------------

  @Test
  void writeStoresTheEntryUnderItsKeyAndKeepsOtherFeatureState() {
    ArgumentCaptor<UnaryOperator<AuthenticatedSession>> mutator =
        ArgumentCaptor.forClass(UnaryOperator.class);
    when(concurrency.updateIfUsable(eq(SID), mutator.capture(), eq("req-1")))
        .thenReturn(
            Mono.just(AuthenticatedSessionConcurrencyResult.applied(usableSession(Map.of()))));

    StepVerifier.create(service().write(SID, entry(), "req-1")).verifyComplete();

    var updated = mutator.getValue().apply(usableSession(Map.of("other.feature", "keep")));
    assertThat(updated.featureState()).containsKey(KEY).containsEntry("other.feature", "keep");
    stored(updated);
    assertThat(read(ACCOUNT_REF, POLICY_NO)).contains(entry());
  }

  @Test
  void writeReplacesAnExistingEntryOfTheSameKey() {
    ArgumentCaptor<UnaryOperator<AuthenticatedSession>> mutator =
        ArgumentCaptor.forClass(UnaryOperator.class);
    when(concurrency.updateIfUsable(eq(SID), mutator.capture(), eq("req-1")))
        .thenReturn(
            Mono.just(AuthenticatedSessionConcurrencyResult.applied(usableSession(Map.of()))));

    StepVerifier.create(service().write(SID, entry(), "req-1")).verifyComplete();

    var updated = mutator.getValue().apply(usableSession(Map.of(KEY, "old-value")));
    assertThat(updated.featureState()).containsOnlyKeys(KEY);
    assertThat(updated.featureState().get(KEY)).isNotEqualTo("old-value").contains("addr1");
  }

  @Test
  void writeStoresOnlyConfigAndIdentifiersNeverMemberData() throws Exception {
    ArgumentCaptor<UnaryOperator<AuthenticatedSession>> mutator =
        ArgumentCaptor.forClass(UnaryOperator.class);
    when(concurrency.updateIfUsable(eq(SID), mutator.capture(), eq("req-1")))
        .thenReturn(
            Mono.just(AuthenticatedSessionConcurrencyResult.applied(usableSession(Map.of()))));

    StepVerifier.create(service().write(SID, entry(), "req-1")).verifyComplete();

    var updated = mutator.getValue().apply(usableSession(Map.of()));
    JsonNode node = new ObjectMapper().readTree(updated.featureState().get(KEY));
    List<String> names = new ArrayList<>();
    node.fieldNames().forEachRemaining(names::add);
    assertThat(names).containsExactlyInAnyOrder("accountRef", "policyNo", "itemConfig");
  }

  @ParameterizedTest
  @EnumSource(
      value = AuthenticatedSessionConcurrencyOutcome.class,
      names = "APPLIED",
      mode = EnumSource.Mode.EXCLUDE)
  void writeCompletesWithoutErrorWhenTheUpdateIsNotApplied(
      AuthenticatedSessionConcurrencyOutcome outcome) {
    when(concurrency.updateIfUsable(any(), any(), any()))
        .thenReturn(Mono.just(AuthenticatedSessionConcurrencyResult.of(outcome)));

    StepVerifier.create(service().write(SID, entry(), "req-1")).verifyComplete();
  }

  @Test
  void writeCompletesWithoutErrorWhenTheUpdateFails() {
    when(concurrency.updateIfUsable(any(), any(), any()))
        .thenReturn(Mono.error(new IllegalStateException("redis down")));

    StepVerifier.create(service().write(SID, entry(), "req-1")).verifyComplete();
  }

  @Test
  void writeCompletesWithoutErrorWhenEncodingFails() {
    TransientJsonPort failingJson = mock(TransientJsonPort.class);
    when(failingJson.write(any())).thenThrow(new IllegalArgumentException("boom"));
    var service =
        new PersonalInformationConfigCacheService(
            sessions, protector, concurrency, failingJson, CLOCK);

    StepVerifier.create(service.write(SID, entry(), "req-1")).verifyComplete();
    verify(concurrency, never()).updateIfUsable(any(), any(), any());
  }

  // ----- guards -------------------------------------------------------------

  @Test
  void requiresItsArguments() {
    var service = service();
    var entry = entry();

    assertThatThrownBy(() -> service.read(null, ACCOUNT_REF, POLICY_NO, "r"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> service.write(null, entry, "r"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> service.write(SID, null, "r"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new PersonalInformationConfigCacheService(
                    null, protector, concurrency, json, CLOCK))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new PersonalInformationConfigCacheService(sessions, null, concurrency, json, CLOCK))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> new PersonalInformationConfigCacheService(sessions, protector, null, json, CLOCK))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new PersonalInformationConfigCacheService(
                    sessions, protector, concurrency, null, CLOCK))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new PersonalInformationConfigCacheService(
                    sessions, protector, concurrency, json, null))
        .isInstanceOf(NullPointerException.class);
  }
}
