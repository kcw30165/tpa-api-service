package com.bct.ngtpa.apiservice.application.usecase.fundcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
 * Corrected (script 047, closing a confirmed Clean Architecture violation):
 * FundComparisonOptionsUseCase no longer depends on {@code FundComparisonOptionsMapper} or {@code
 * FundComparisonOptionsResponse} (both adapter.in.web types) -- it returns the raw,
 * application-layer {@link RawFundComparisonOptionsData} directly. Mapping to the public response
 * is now the controller's responsibility, mirroring {@code GetFundFactSheetService}'s real,
 * confirmed pattern.
 */
class FundComparisonOptionsUseCaseTest {

  private final FundComparisonOptionsSourcePort sourcePort =
      mock(FundComparisonOptionsSourcePort.class);

  private FundComparisonOptionsUseCase newUseCase() {
    return new FundComparisonOptionsUseCase(sourcePort);
  }

  private FetchFundComparisonOptionsCommand command() {
    return new FetchFundComparisonOptionsCommand("00000008802", "DB", ActorUserRole.ROLE_USER_MBR);
  }

  private RawFundComparisonOptionsData rawData() {
    return new RawFundComparisonOptionsData(
        "10D,1M,3M,6M,1Y,YTD,3Y",
        List.of(new RawFundComparisonOption("FAEU", "Fidelity Advantage Portfolio Fund", "")));
  }

  // ----- happy path: the port's raw result passes through unchanged -------------
  @Test
  void fetchesThroughThePortAndReturnsTheRawResultUnchanged() {
    var expectedRaw = rawData();
    when(sourcePort.fetchOptions(any())).thenReturn(Mono.just(expectedRaw));

    StepVerifier.create(newUseCase().getOptions(command()))
        .assertNext(result -> assertThat(result).isSameAs(expectedRaw))
        .verifyComplete();

    verify(sourcePort).fetchOptions(command());
  }

  // ----- port failure: propagates unchanged --------------------------------------
  @Test
  void aRetrievalFailureFromTheSourcePassesThroughUnchanged() {
    var original = FundComparisonRetrievalException.systemFailure();
    when(sourcePort.fetchOptions(any())).thenReturn(Mono.error(original));

    StepVerifier.create(newUseCase().getOptions(command()))
        .expectErrorSatisfies(error -> assertThat(error).isSameAs(original))
        .verify();
  }

  // ----- defensive wrap: any OTHER exception type becomes the generic failure -----
  @Test
  void anUnexpectedExceptionFromThePortIsWrappedAsFundComparisonRetrievalException() {
    when(sourcePort.fetchOptions(any()))
        .thenReturn(Mono.error(new IllegalStateException("connection refused")));

    StepVerifier.create(newUseCase().getOptions(command()))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(FundComparisonRetrievalException.class);
              assertThat(error.getMessage()).doesNotContain("connection refused");
            })
        .verify();
  }

  // ----- constructor / method null-checks -----------------------------------------
  @Test
  void constructorAndMethodArgumentsRequireNonNull() {
    assertThatThrownBy(() -> new FundComparisonOptionsUseCase(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("sourcePort");
    assertThatThrownBy(() -> newUseCase().getOptions(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("command");
  }
}
