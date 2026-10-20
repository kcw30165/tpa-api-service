package com.bct.ngtpa.apiservice.application.usecase.fundcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonOptionsCommand;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonOptionsCacheEntry;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData.RawFundComparisonOption;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRetrievalException;
import com.bct.ngtpa.apiservice.application.port.out.FundComparisonOptionsSourcePort;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Fund Comparison M5.3: the options use case writes fetched options to the session cache. */
class FundComparisonOptionsUseCaseCacheTest {

  private static final AuthenticatedSessionId SID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final String ACCOUNT_REF = "acc_abcdefghijklmnopqrstuvwx";

  private final FundComparisonOptionsSourcePort sourcePort =
      mock(FundComparisonOptionsSourcePort.class);
  private final FundComparisonOptionsCacheService cache =
      mock(FundComparisonOptionsCacheService.class);

  private FundComparisonOptionsUseCase useCase() {
    return new FundComparisonOptionsUseCase(sourcePort, cache);
  }

  private static FetchFundComparisonOptionsCommand command() {
    return new FetchFundComparisonOptionsCommand("00000008802", "DB", ActorUserRole.ROLE_USER_MBR);
  }

  private static RawFundComparisonOptionsData raw() {
    return new RawFundComparisonOptionsData(
        "10D,1M",
        List.of(
            new RawFundComparisonOption("FAEU", "A Fund", ""),
            new RawFundComparisonOption("FBAU", "B Fund", "")));
  }

  @Test
  void writesTheFetchedOptionsForTheSessionAndReturnsThem() {
    var raw = raw();
    when(sourcePort.fetchOptions(any())).thenReturn(Mono.just(raw));
    when(cache.write(any(), any(), any())).thenReturn(Mono.empty());

    StepVerifier.create(useCase().getOptionsAndCache(command(), Optional.of(SID), ACCOUNT_REF, "req-1"))
        .assertNext(result -> assertThat(result).isSameAs(raw))
        .verifyComplete();

    ArgumentCaptor<FundComparisonOptionsCacheEntry> entry =
        ArgumentCaptor.forClass(FundComparisonOptionsCacheEntry.class);
    verify(cache).write(eq(SID), entry.capture(), eq("req-1"));
    assertThat(entry.getValue())
        .isEqualTo(
            new FundComparisonOptionsCacheEntry(
                ACCOUNT_REF, "00000008802", "10D,1M", List.of("FAEU", "FBAU")));
  }

  @Test
  void withoutASessionReturnsTheOptionsAndWritesNothing() {
    var raw = raw();
    when(sourcePort.fetchOptions(any())).thenReturn(Mono.just(raw));

    StepVerifier.create(useCase().getOptionsAndCache(command(), Optional.empty(), ACCOUNT_REF, "req-1"))
        .assertNext(result -> assertThat(result).isSameAs(raw))
        .verifyComplete();

    verifyNoInteractions(cache);
  }

  @Test
  void anAccountRefThatCannotBeCachedStillReturnsTheOptions() {
    var raw = raw();
    when(sourcePort.fetchOptions(any())).thenReturn(Mono.just(raw));

    StepVerifier.create(useCase().getOptionsAndCache(command(), Optional.of(SID), " ", "req-1"))
        .assertNext(result -> assertThat(result).isSameAs(raw))
        .verifyComplete();

    verifyNoInteractions(cache);
  }

  @Test
  void aFetchFailureIsPropagatedAndNothingIsWritten() {
    var failure = FundComparisonRetrievalException.systemFailure();
    when(sourcePort.fetchOptions(any())).thenReturn(Mono.error(failure));

    StepVerifier.create(useCase().getOptionsAndCache(command(), Optional.of(SID), ACCOUNT_REF, "req-1"))
        .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
        .verify();

    verifyNoInteractions(cache);
  }

  @Test
  void requiresTheCacheAndTheSessionOptional() {
    var useCase = useCase();
    var command = command();

    assertThatThrownBy(() -> new FundComparisonOptionsUseCase(sourcePort, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("cache");
    assertThatThrownBy(() -> useCase.getOptionsAndCache(command, null, ACCOUNT_REF, "req-1"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("sessionId");
  }
}
