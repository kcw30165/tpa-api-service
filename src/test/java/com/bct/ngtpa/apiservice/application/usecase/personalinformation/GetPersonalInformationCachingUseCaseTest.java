package com.bct.ngtpa.apiservice.application.usecase.personalinformation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Design section 5: the GET writes the field-access config of the selected account to the session.
 */
class GetPersonalInformationCachingUseCaseTest {

  private static final AuthenticatedSessionId SID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final String ACCOUNT_REF = "acc_abcdefghijklmnopqrstuvwx";

  private final GetPersonalInformationUseCase delegate = mock(GetPersonalInformationUseCase.class);
  private final PersonalInformationConfigCacheService cache =
      mock(PersonalInformationConfigCacheService.class);

  private GetPersonalInformationCachingUseCase useCase() {
    return new GetPersonalInformationCachingUseCase(delegate, cache);
  }

  private static GetPersonalInformationCommand command() {
    return new GetPersonalInformationCommand(
        "en", "JP", "policy-1", "cert-1", "user-1", ActorUserRole.ROLE_USER_MBR);
  }

  private static PersonalInformationResult resultWithConfig() {
    Map<String, String> config = new LinkedHashMap<>();
    config.put("addr1", "EDITABLE_COM");
    config.put("email", "READONLY");
    return new PersonalInformationResult(Map.of("email", "a@b.test"), config);
  }

  @Test
  void writesTheConfigAndIdentifiersForTheSessionAndReturnsTheResult() {
    var result = resultWithConfig();
    when(delegate.execute(any())).thenReturn(Mono.just(result));
    when(cache.write(any(), any(), any())).thenReturn(Mono.empty());

    StepVerifier.create(
            useCase().executeAndCache(command(), Optional.of(SID), ACCOUNT_REF, "req-1"))
        .assertNext(actual -> assertThat(actual).isSameAs(result))
        .verifyComplete();

    ArgumentCaptor<PersonalInformationConfigCacheEntry> entry =
        ArgumentCaptor.forClass(PersonalInformationConfigCacheEntry.class);
    verify(cache).write(eq(SID), entry.capture(), eq("req-1"));
    assertThat(entry.getValue())
        .isEqualTo(
            new PersonalInformationConfigCacheEntry(
                ACCOUNT_REF, "policy-1", Map.of("addr1", "EDITABLE_COM", "email", "READONLY")));
  }

  @Test
  void withoutASessionReturnsTheResultAndWritesNothing() {
    var result = resultWithConfig();
    when(delegate.execute(any())).thenReturn(Mono.just(result));

    StepVerifier.create(
            useCase().executeAndCache(command(), Optional.empty(), ACCOUNT_REF, "req-1"))
        .assertNext(actual -> assertThat(actual).isSameAs(result))
        .verifyComplete();

    verifyNoInteractions(cache);
  }

  @Test
  void aBlankAccountRefStillReturnsTheResultAndWritesNothing() {
    var result = resultWithConfig();
    when(delegate.execute(any())).thenReturn(Mono.just(result));

    StepVerifier.create(useCase().executeAndCache(command(), Optional.of(SID), " ", "req-1"))
        .assertNext(actual -> assertThat(actual).isSameAs(result))
        .verifyComplete();

    verifyNoInteractions(cache);
  }

  @Test
  void anEmptyConfigStillReturnsTheResultAndWritesNothing() {
    var result = new PersonalInformationResult(Map.of("email", "a@b.test"), Map.of());
    when(delegate.execute(any())).thenReturn(Mono.just(result));

    StepVerifier.create(
            useCase().executeAndCache(command(), Optional.of(SID), ACCOUNT_REF, "req-1"))
        .assertNext(actual -> assertThat(actual).isSameAs(result))
        .verifyComplete();

    verifyNoInteractions(cache);
  }

  @Test
  void aCacheWriteFailureDoesNotFailTheCall() {
    var result = resultWithConfig();
    when(delegate.execute(any())).thenReturn(Mono.just(result));
    when(cache.write(any(), any(), any()))
        .thenReturn(Mono.error(new IllegalStateException("simulated cache write failure")));

    StepVerifier.create(
            useCase().executeAndCache(command(), Optional.of(SID), ACCOUNT_REF, "req-1"))
        .assertNext(actual -> assertThat(actual).isSameAs(result))
        .verifyComplete();
  }

  @Test
  void aFetchFailureIsPropagatedAndNothingIsWritten() {
    var failure = new IllegalStateException("downstream failed");
    when(delegate.execute(any())).thenReturn(Mono.error(failure));

    StepVerifier.create(
            useCase().executeAndCache(command(), Optional.of(SID), ACCOUNT_REF, "req-1"))
        .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
        .verify();

    verifyNoInteractions(cache);
  }

  @Test
  void executeDelegatesWithoutCaching() {
    var result = resultWithConfig();
    when(delegate.execute(any())).thenReturn(Mono.just(result));

    assertThat(useCase().execute(command()).block()).isSameAs(result);

    verifyNoInteractions(cache);
  }

  @Test
  void cachingUseCaseRequiresItsArguments() {
    var useCase = useCase();
    var command = command();

    assertThatThrownBy(() -> new GetPersonalInformationCachingUseCase(null, cache))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new GetPersonalInformationCachingUseCase(delegate, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> useCase.executeAndCache(null, Optional.of(SID), ACCOUNT_REF, "req-1"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> useCase.executeAndCache(command, null, ACCOUNT_REF, "req-1"))
        .isInstanceOf(NullPointerException.class);
  }
}
