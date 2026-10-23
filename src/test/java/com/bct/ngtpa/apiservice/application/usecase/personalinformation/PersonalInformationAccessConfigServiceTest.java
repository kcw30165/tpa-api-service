package com.bct.ngtpa.apiservice.application.usecase.personalinformation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.PersonalInformationConfigCacheEntry;
import com.bct.ngtpa.apiservice.application.dto.PersonalInformationResult;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.port.in.GetPersonalInformationUseCase;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Sub-task 3.4b: where the PUT gets the field-access config from (design D-13). */
class PersonalInformationAccessConfigServiceTest {

  private static final AuthenticatedSessionId SID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final String ACCOUNT_REF = "acc_abcdefghijklmnopqrstuvwx";
  private static final String POLICY_NO = "policy-1";
  private static final String REQUEST_ID = "req-1";
  private static final Map<String, String> CACHED =
      Map.of("addr1", "EDITABLE_COM", "email", "READONLY");
  private static final Map<String, String> FRESH =
      Map.of("addr1", "EDITABLE_COM", "email", "EDITABLE_OPTION");

  private final GetPersonalInformationUseCase retrieval = mock(GetPersonalInformationUseCase.class);
  private final PersonalInformationConfigCacheService cache =
      mock(PersonalInformationConfigCacheService.class);

  private PersonalInformationAccessConfigService service() {
    return new PersonalInformationAccessConfigService(retrieval, cache);
  }

  private static GetPersonalInformationCommand command() {
    return new GetPersonalInformationCommand(
        "en", "JP", POLICY_NO, "cert-1", "user-1", ActorUserRole.ROLE_USER_MBR);
  }

  private void cacheHolds(Map<String, String> config) {
    when(cache.read(any(), any(), any(), any()))
        .thenReturn(
            Mono.just(
                Optional.of(
                    new PersonalInformationConfigCacheEntry(ACCOUNT_REF, POLICY_NO, config))));
  }

  private void cacheIsEmpty() {
    when(cache.read(any(), any(), any(), any())).thenReturn(Mono.just(Optional.empty()));
  }

  private void retrievalReturns(Map<String, String> config) {
    when(retrieval.executeAndCache(any(), any(), any(), any()))
        .thenReturn(Mono.just(new PersonalInformationResult(Map.of("email", "a@b.test"), config)));
  }

  @Test
  void cacheHitReturnsTheCachedConfigAndDoesNotFetch() {
    cacheHolds(CACHED);

    StepVerifier.create(service().resolve(command(), Optional.of(SID), ACCOUNT_REF, REQUEST_ID))
        .assertNext(
            resolved -> {
              assertThat(resolved.fromCache()).isTrue();
              assertThat(resolved.itemConfig()).isEqualTo(CACHED);
            })
        .verifyComplete();

    verify(retrieval, never()).executeAndCache(any(), any(), any(), any());
  }

  @Test
  void cacheMissFetchesAndReturnsTheFreshConfig() {
    cacheIsEmpty();
    retrievalReturns(FRESH);

    StepVerifier.create(service().resolve(command(), Optional.of(SID), ACCOUNT_REF, REQUEST_ID))
        .assertNext(
            resolved -> {
              assertThat(resolved.fromCache()).isFalse();
              assertThat(resolved.itemConfig()).isEqualTo(FRESH);
            })
        .verifyComplete();

    verify(retrieval).executeAndCache(command(), Optional.of(SID), ACCOUNT_REF, REQUEST_ID);
  }

  @Test
  void cacheReadUsesTheAccountRefAndTheCommandPolicyNo() {
    cacheIsEmpty();
    retrievalReturns(FRESH);

    StepVerifier.create(service().resolve(command(), Optional.of(SID), ACCOUNT_REF, REQUEST_ID))
        .expectNextCount(1)
        .verifyComplete();

    verify(cache).read(SID, ACCOUNT_REF, POLICY_NO, REQUEST_ID);
  }

  @Test
  void noSessionFetchesWithoutReadingTheCache() {
    retrievalReturns(FRESH);

    StepVerifier.create(service().resolve(command(), Optional.empty(), ACCOUNT_REF, REQUEST_ID))
        .assertNext(
            resolved -> {
              assertThat(resolved.fromCache()).isFalse();
              assertThat(resolved.itemConfig()).isEqualTo(FRESH);
            })
        .verifyComplete();

    verifyNoInteractions(cache);
    verify(retrieval).executeAndCache(command(), Optional.empty(), ACCOUNT_REF, REQUEST_ID);
  }

  @Test
  void refreshAlwaysFetchesAndNeverReadsTheCache() {
    retrievalReturns(FRESH);

    StepVerifier.create(service().refresh(command(), Optional.of(SID), ACCOUNT_REF, REQUEST_ID))
        .assertNext(config -> assertThat(config).isEqualTo(FRESH))
        .verifyComplete();

    verifyNoInteractions(cache);
    verify(retrieval).executeAndCache(command(), Optional.of(SID), ACCOUNT_REF, REQUEST_ID);
  }

  @Test
  void aFetchFailureOnACacheMissIsPropagated() {
    cacheIsEmpty();
    var failure = new IllegalStateException("downstream unavailable");
    when(retrieval.executeAndCache(any(), any(), any(), any())).thenReturn(Mono.error(failure));

    StepVerifier.create(service().resolve(command(), Optional.of(SID), ACCOUNT_REF, REQUEST_ID))
        .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
        .verify();
  }

  @Test
  void aFetchFailureOnRefreshIsPropagated() {
    var failure = new IllegalStateException("downstream unavailable");
    when(retrieval.executeAndCache(any(), any(), any(), any())).thenReturn(Mono.error(failure));

    StepVerifier.create(service().refresh(command(), Optional.of(SID), ACCOUNT_REF, REQUEST_ID))
        .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
        .verify();
  }

  @Test
  void anEmptyFetchedConfigIsReturnedAsIs() {
    cacheIsEmpty();
    retrievalReturns(Map.of());

    StepVerifier.create(service().resolve(command(), Optional.of(SID), ACCOUNT_REF, REQUEST_ID))
        .assertNext(
            resolved -> {
              assertThat(resolved.fromCache()).isFalse();
              assertThat(resolved.itemConfig()).isEmpty();
            })
        .verifyComplete();
  }

  @Test
  void accessConfigServiceRequiresItsArguments() {
    var service = service();
    var command = command();

    assertThatThrownBy(() -> new PersonalInformationAccessConfigService(null, cache))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new PersonalInformationAccessConfigService(retrieval, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> service.resolve(null, Optional.of(SID), ACCOUNT_REF, REQUEST_ID))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> service.resolve(command, null, ACCOUNT_REF, REQUEST_ID))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> service.refresh(null, Optional.of(SID), ACCOUNT_REF, REQUEST_ID))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> service.refresh(command, null, ACCOUNT_REF, REQUEST_ID))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void resolvedConfigCopiesTheMapAndIsImmutable() {
    Map<String, String> source = new LinkedHashMap<>();
    source.put("a", "EDITABLE_COM");
    source.put("b", "READONLY");

    var resolved = new PersonalInformationAccessConfigService.Resolved(source, true);
    source.put("c", "HIDDEN");

    assertThat(resolved.itemConfig())
        .containsExactly(Map.entry("a", "EDITABLE_COM"), Map.entry("b", "READONLY"));
    Map<String, String> itemConfig = resolved.itemConfig();
    assertThatThrownBy(() -> itemConfig.put("x", "y"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> new PersonalInformationAccessConfigService.Resolved(null, false))
        .isInstanceOf(NullPointerException.class);
  }
}
