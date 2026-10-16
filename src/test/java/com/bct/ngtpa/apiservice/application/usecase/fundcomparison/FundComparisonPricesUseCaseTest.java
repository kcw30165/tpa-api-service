package com.bct.ngtpa.apiservice.application.usecase.fundcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonPricesCommand;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonPricesUseCaseCommand;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceData;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRetrievalException;
import com.bct.ngtpa.apiservice.application.port.out.FundComparisonPriceSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Fail-first test for Fund Comparison Milestone 3, sub-task 3.3 (prices use case), per
 * docs/design/13-fund-comparison/07-implementation-plan.md section 7.4 and
 * 01-solution-design.md section 1.4 (inclusive date rules: R=15/10/2026, 10D -> 06/10/2026 to
 * 15/10/2026).
 */
class FundComparisonPricesUseCaseTest {

  private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 10, 15);

  private ReferenceDatePort referenceDatePort(LocalDate referenceDate) {
    return accountEnv -> Mono.just(referenceDate);
  }

  private ReferenceDatePort failingReferenceDatePort(RuntimeException error) {
    return accountEnv -> Mono.error(error);
  }

  private FetchFundComparisonPricesUseCaseCommand command(
      List<String> fundCodes, String timeRange) {
    return new FetchFundComparisonPricesUseCaseCommand(
        "00000008802", "DB", ActorUserRole.ROLE_USER_MBR, fundCodes, timeRange);
  }

  @Test
  void resolvesTheInclusiveDateRangeFromTheReferenceDatePort() {
    var captured = new AtomicReference<FetchFundComparisonPricesCommand>();
    FundComparisonPriceSourcePort sourcePort =
        portCommand -> {
          captured.set(portCommand);
          return Mono.just(new RawFundPriceData(List.of()));
        };
    var useCase = new FundComparisonPricesUseCase(sourcePort, referenceDatePort(REFERENCE_DATE));

    StepVerifier.create(useCase.getPrices(command(List.of("FAEU"), "10D")))
        .expectNextCount(1)
        .verifyComplete();

    var portCommand = captured.get();
    assertThat(portCommand.fromDate()).isEqualTo(LocalDate.of(2026, 10, 6));
    assertThat(portCommand.toDate()).isEqualTo(REFERENCE_DATE);
  }

  @Test
  void joinsFundCodesWithCommasAndNoSpaces() {
    var captured = new AtomicReference<FetchFundComparisonPricesCommand>();
    FundComparisonPriceSourcePort sourcePort =
        portCommand -> {
          captured.set(portCommand);
          return Mono.just(new RawFundPriceData(List.of()));
        };
    var useCase = new FundComparisonPricesUseCase(sourcePort, referenceDatePort(REFERENCE_DATE));

    StepVerifier.create(useCase.getPrices(command(List.of("FAEU", "FBAU"), "10D")))
        .expectNextCount(1)
        .verifyComplete();

    assertThat(captured.get().fundList()).isEqualTo("FAEU,FBAU");
  }

  @Test
  void passesPolicyNoAccountEnvAndActorUserRoleThroughToThePortCommand() {
    var captured = new AtomicReference<FetchFundComparisonPricesCommand>();
    FundComparisonPriceSourcePort sourcePort =
        portCommand -> {
          captured.set(portCommand);
          return Mono.just(new RawFundPriceData(List.of()));
        };
    var useCase = new FundComparisonPricesUseCase(sourcePort, referenceDatePort(REFERENCE_DATE));

    StepVerifier.create(useCase.getPrices(command(List.of("FAEU"), "10D")))
        .expectNextCount(1)
        .verifyComplete();

    var portCommand = captured.get();
    assertThat(portCommand.policyNo()).isEqualTo("00000008802");
    assertThat(portCommand.accountEnv()).isEqualTo("DB");
    assertThat(portCommand.actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
  }

  @Test
  void assemblesTheResultWithTheComputedDateRangeRequestedFundCodesAndRawData() {
    var rawData = new RawFundPriceData(List.of());
    FundComparisonPriceSourcePort sourcePort = portCommand -> Mono.just(rawData);
    var useCase = new FundComparisonPricesUseCase(sourcePort, referenceDatePort(REFERENCE_DATE));

    StepVerifier.create(useCase.getPrices(command(List.of("FAEU", "FBAU"), "10D")))
        .assertNext(
            result -> {
              assertThat(result.fromDate()).isEqualTo(LocalDate.of(2026, 10, 6));
              assertThat(result.toDate()).isEqualTo(REFERENCE_DATE);
              assertThat(result.fundCodes()).containsExactly("FAEU", "FBAU");
              assertThat(result.rawData()).isSameAs(rawData);
            })
        .verifyComplete();
  }

  @Test
  void referenceDatePortFailureIsWrappedAsRetrievalUnavailable() {
    FundComparisonPriceSourcePort sourcePort =
        portCommand -> Mono.just(new RawFundPriceData(List.of()));
    var useCase =
        new FundComparisonPricesUseCase(
            sourcePort, failingReferenceDatePort(new IllegalStateException("downstream failure")));

    StepVerifier.create(useCase.getPrices(command(List.of("FAEU"), "10D")))
        .expectErrorMatches(FundComparisonRetrievalException.class::isInstance)
        .verify();
  }

  @Test
  void referenceDatePortFailureThatIsAlreadyARetrievalExceptionIsPassedThroughUnchanged() {
    var original = FundComparisonRetrievalException.systemFailure();
    FundComparisonPriceSourcePort sourcePort =
        portCommand -> Mono.just(new RawFundPriceData(List.of()));
    var useCase = new FundComparisonPricesUseCase(sourcePort, failingReferenceDatePort(original));

    StepVerifier.create(useCase.getPrices(command(List.of("FAEU"), "10D")))
        .expectErrorMatches(original::equals)
        .verify();
  }

  @Test
  void sourcePortFailureIsWrappedAsRetrievalUnavailable() {
    FundComparisonPriceSourcePort sourcePort =
        portCommand -> Mono.error(new IllegalStateException("downstream price failure"));
    var useCase = new FundComparisonPricesUseCase(sourcePort, referenceDatePort(REFERENCE_DATE));

    StepVerifier.create(useCase.getPrices(command(List.of("FAEU"), "10D")))
        .expectErrorMatches(FundComparisonRetrievalException.class::isInstance)
        .verify();
  }

  @Test
  void sourcePortFailureThatIsAlreadyARetrievalExceptionIsPassedThroughUnchanged() {
    var original = FundComparisonRetrievalException.systemFailure();
    FundComparisonPriceSourcePort sourcePort = portCommand -> Mono.error(original);
    var useCase = new FundComparisonPricesUseCase(sourcePort, referenceDatePort(REFERENCE_DATE));

    StepVerifier.create(useCase.getPrices(command(List.of("FAEU"), "10D")))
        .expectErrorMatches(original::equals)
        .verify();
  }

  @Test
  void nullCommandThrowsNullPointerException() {
    FundComparisonPriceSourcePort sourcePort =
        portCommand -> Mono.just(new RawFundPriceData(List.of()));
    var useCase = new FundComparisonPricesUseCase(sourcePort, referenceDatePort(REFERENCE_DATE));

    assertThatThrownBy(() -> useCase.getPrices(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void constructorRequiresBothDependencies() {
    FundComparisonPriceSourcePort sourcePort =
        portCommand -> Mono.just(new RawFundPriceData(List.of()));
    ReferenceDatePort referenceDatePort = referenceDatePort(REFERENCE_DATE);

    assertThatThrownBy(() -> new FundComparisonPricesUseCase(null, referenceDatePort))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("sourcePort");
    assertThatThrownBy(() -> new FundComparisonPricesUseCase(sourcePort, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("referenceDatePort");
  }
}
