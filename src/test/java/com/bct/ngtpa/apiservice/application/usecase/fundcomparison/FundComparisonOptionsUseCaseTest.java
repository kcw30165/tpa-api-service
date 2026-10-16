package com.bct.ngtpa.apiservice.application.usecase.fundcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.mapper.FundComparisonOptionsMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.response.FundComparisonOptionsResponse;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonOptionsCommand;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData.RawFundComparisonOption;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRetrievalException;
import com.bct.ngtpa.apiservice.application.port.out.FundComparisonOptionsSourcePort;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Fail-first: FundComparisonOptionsUseCase does not exist yet.
 *
 * <p>Confirmed design (script 035 investigation): mirrors a simplified {@code
 * GetFundFactSheetService}-style orchestration -- calls {@link
 * FundComparisonOptionsSourcePort#fetchOptions} (the port, never a concrete adapter) then maps the
 * result through {@link FundComparisonOptionsMapper}. No caching, no session mutation (confirmed
 * non-goal, unlike {@code FundPriceModeUseCase}'s session-stamping pattern). Any exception that is
 * not already a {@link FundComparisonRetrievalException} is defensively wrapped into one, mirroring
 * {@code GetFundFactSheetService}'s exact safety net.
 */
class FundComparisonOptionsUseCaseTest {

  private final FundComparisonOptionsSourcePort sourcePort =
      mock(FundComparisonOptionsSourcePort.class);
  private final FundComparisonOptionsMapper mapper = mock(FundComparisonOptionsMapper.class);

  private FundComparisonOptionsUseCase newUseCase() {
    return new FundComparisonOptionsUseCase(sourcePort, mapper);
  }

  private FetchFundComparisonOptionsCommand command() {
    return new FetchFundComparisonOptionsCommand("00000008802", "DB", ActorUserRole.ROLE_USER_MBR);
  }

  private RawFundComparisonOptionsData rawData() {
    return new RawFundComparisonOptionsData(
        "10D,1M,3M,6M,1Y,YTD,3Y",
        List.of(new RawFundComparisonOption("FAEU", "Fidelity Advantage Portfolio Fund", "")));
  }

  private FundComparisonOptionsResponse response() {
    return new FundComparisonOptionsResponse(
        List.of("10D", "1M", "3M", "6M", "1Y", "YTD", "3Y"), "10D", List.of());
  }

  // ----- happy path: port -> mapper, exact arguments passed through -------------
  @Test
  void fetchesThroughThePortAndMapsThroughTheMapperWithTheGivenLanguage() {
    // response() constructs a NEW record instance on every call -- capture it ONCE
    // into a local so the identity check below compares the mock's actual return
    // value against itself, not against a freshly-constructed (value-equal but
    // instance-distinct) record.
    var expectedResponse = response();
    when(sourcePort.fetchOptions(any())).thenReturn(Mono.just(rawData()));
    when(mapper.toResponse(rawData(), "zh_HK")).thenReturn(expectedResponse);

    StepVerifier.create(newUseCase().getOptions(command(), "zh_HK"))
        .assertNext(result -> assertThat(result).isSameAs(expectedResponse))
        .verifyComplete();

    verify(sourcePort).fetchOptions(command());
    verify(mapper).toResponse(rawData(), "zh_HK");
  }

  // ----- port failure: propagates, mapper never called -------------------------
  @Test
  void sourcePortFailureNeverCallsTheMapperAndPropagatesAsFundComparisonRetrievalException() {
    when(sourcePort.fetchOptions(any()))
        .thenReturn(Mono.error(FundComparisonRetrievalException.systemFailure()));

    StepVerifier.create(newUseCase().getOptions(command(), "en"))
        .expectError(FundComparisonRetrievalException.class)
        .verify();

    verifyNoInteractions(mapper);
  }

  @Test
  void aRetrievalFailureFromTheSourcePassesThroughUnchanged() {
    var original = FundComparisonRetrievalException.systemFailure();
    when(sourcePort.fetchOptions(any())).thenReturn(Mono.error(original));

    StepVerifier.create(newUseCase().getOptions(command(), "en"))
        .expectErrorSatisfies(error -> assertThat(error).isSameAs(original))
        .verify();
  }

  // ----- mapper throws (e.g. all-invalid time-range) -> propagates unchanged ----
  @Test
  void aRetrievalExceptionThrownByTheMapperPassesThroughUnchanged() {
    var original = FundComparisonRetrievalException.systemFailure();
    when(sourcePort.fetchOptions(any())).thenReturn(Mono.just(rawData()));
    when(mapper.toResponse(any(), any())).thenThrow(original);

    StepVerifier.create(newUseCase().getOptions(command(), "en"))
        .expectErrorSatisfies(error -> assertThat(error).isSameAs(original))
        .verify();
  }

  // ----- defensive wrap: any OTHER exception type becomes the generic failure ---
  @Test
  void anUnexpectedExceptionFromTheMapperIsWrappedAsFundComparisonRetrievalException() {
    when(sourcePort.fetchOptions(any())).thenReturn(Mono.just(rawData()));
    when(mapper.toResponse(any(), any())).thenThrow(new IllegalStateException("unexpected"));

    StepVerifier.create(newUseCase().getOptions(command(), "en"))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(FundComparisonRetrievalException.class);
              assertThat(error.getMessage()).doesNotContain("unexpected");
            })
        .verify();
  }

  @Test
  void anUnexpectedExceptionFromThePortIsWrappedAsFundComparisonRetrievalException() {
    when(sourcePort.fetchOptions(any()))
        .thenReturn(Mono.error(new IllegalStateException("connection refused")));

    StepVerifier.create(newUseCase().getOptions(command(), "en"))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(FundComparisonRetrievalException.class);
              assertThat(error.getMessage()).doesNotContain("connection refused");
            })
        .verify();
  }

  // ----- constructor / method null-checks ---------------------------------------
  @Test
  void constructorAndMethodArgumentsRequireNonNull() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new FundComparisonOptionsUseCase(null, mapper))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("sourcePort");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new FundComparisonOptionsUseCase(sourcePort, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("mapper");
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> newUseCase().getOptions(null, "en"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("command");
  }
}
