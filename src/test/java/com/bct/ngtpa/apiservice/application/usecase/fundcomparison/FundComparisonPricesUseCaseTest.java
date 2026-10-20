package com.bct.ngtpa.apiservice.application.usecase.fundcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonPricesCommand;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonPricesUseCaseCommand;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceData;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRequestInvalidException;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRetrievalException;
import com.bct.ngtpa.apiservice.application.exception.FundPriceInvalidDateException;
import com.bct.ngtpa.apiservice.application.port.out.FundComparisonPriceSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Fetch-and-assemble part of the prices use case ({@code fetchPrices}, already-matched selection),
 * per docs/design/13-fund-comparison/01-solution-design.md section 1.4 (R=15/10/2026, 10D -&gt;
 * 06/10/2026 to 15/10/2026). Membership validation is covered by {@code
 * FundComparisonPricesUseCaseMembershipTest}.
 */
class FundComparisonPricesUseCaseTest {

  private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 10, 15);

  private final FundComparisonOptionsUseCase optionsUseCase =
      mock(FundComparisonOptionsUseCase.class);
  private final FundComparisonOptionsCacheService cache =
      mock(FundComparisonOptionsCacheService.class);

  private static ReferenceDatePort referenceDatePort(LocalDate referenceDate) {
    return accountEnv -> Mono.just(referenceDate);
  }

  private static ReferenceDatePort failingReferenceDatePort(RuntimeException error) {
    return accountEnv -> Mono.error(error);
  }

  private static FundComparisonPriceSourcePort capturing(
      AtomicReference<FetchFundComparisonPricesCommand> captured) {
    return portCommand -> {
      captured.set(portCommand);
      return Mono.just(new RawFundPriceData(List.of()));
    };
  }

  private FundComparisonPricesUseCase useCase(
      FundComparisonPriceSourcePort sourcePort, ReferenceDatePort referenceDatePort) {
    return new FundComparisonPricesUseCase(sourcePort, referenceDatePort, optionsUseCase, cache);
  }

  private static FetchFundComparisonPricesUseCaseCommand command(
      List<String> fundCodes, String timeRange) {
    return new FetchFundComparisonPricesUseCaseCommand(
        "00000008802", "DB", ActorUserRole.ROLE_USER_MBR, fundCodes, timeRange);
  }

  @Test
  void resolvesTheInclusiveDateRangeFromTheReferenceDatePort() {
    var captured = new AtomicReference<FetchFundComparisonPricesCommand>();
    var useCase = useCase(capturing(captured), referenceDatePort(REFERENCE_DATE));

    StepVerifier.create(useCase.fetchPrices(command(List.of("FAEU"), "10D")))
        .expectNextCount(1)
        .verifyComplete();

    assertThat(captured.get().fromDate()).isEqualTo(LocalDate.of(2026, 10, 6));
    assertThat(captured.get().toDate()).isEqualTo(REFERENCE_DATE);
  }

  @Test
  void joinsFundCodesWithCommasAndNoSpaces() {
    var captured = new AtomicReference<FetchFundComparisonPricesCommand>();
    var useCase = useCase(capturing(captured), referenceDatePort(REFERENCE_DATE));

    StepVerifier.create(useCase.fetchPrices(command(List.of("FAEU", "FBAU", "FCSU"), "1M")))
        .expectNextCount(1)
        .verifyComplete();

    assertThat(captured.get().fundList()).isEqualTo("FAEU,FBAU,FCSU");
  }

  @Test
  void passesPolicyNoAccountEnvAndActorUserRoleThroughToThePortCommand() {
    var captured = new AtomicReference<FetchFundComparisonPricesCommand>();
    var useCase = useCase(capturing(captured), referenceDatePort(REFERENCE_DATE));

    StepVerifier.create(useCase.fetchPrices(command(List.of("FAEU"), "10D")))
        .expectNextCount(1)
        .verifyComplete();

    assertThat(captured.get().policyNo()).isEqualTo("00000008802");
    assertThat(captured.get().accountEnv()).isEqualTo("DB");
    assertThat(captured.get().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
  }

  @Test
  void assemblesTheResultWithTheComputedDateRangeRequestedFundCodesAndRawData() {
    var rawData = new RawFundPriceData(List.of());
    var useCase = useCase(portCommand -> Mono.just(rawData), referenceDatePort(REFERENCE_DATE));

    StepVerifier.create(useCase.fetchPrices(command(List.of("FAEU", "FBAU"), "10D")))
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
    var captured = new AtomicReference<FetchFundComparisonPricesCommand>();
    var useCase =
        useCase(
            capturing(captured),
            failingReferenceDatePort(new IllegalStateException("downstream failure")));

    StepVerifier.create(useCase.fetchPrices(command(List.of("FAEU"), "10D")))
        .expectErrorSatisfies(
            error -> assertThat(error).isInstanceOf(FundComparisonRetrievalException.class))
        .verify();

    assertThat(captured.get()).isNull();
  }

  @Test
  void referenceDatePortFailureThatIsAlreadyARetrievalExceptionIsPassedThroughUnchanged() {
    var original = FundComparisonRetrievalException.systemFailure();
    var useCase =
        useCase(
            portCommand -> Mono.just(new RawFundPriceData(List.of())),
            failingReferenceDatePort(original));

    StepVerifier.create(useCase.fetchPrices(command(List.of("FAEU"), "10D")))
        .expectErrorSatisfies(error -> assertThat(error).isSameAs(original))
        .verify();
  }

  @Test
  void sourcePortFailureIsWrappedAsRetrievalUnavailable() {
    var useCase =
        useCase(
            portCommand -> Mono.error(new IllegalStateException("downstream price failure")),
            referenceDatePort(REFERENCE_DATE));

    StepVerifier.create(useCase.fetchPrices(command(List.of("FAEU"), "10D")))
        .expectErrorSatisfies(
            error -> assertThat(error).isInstanceOf(FundComparisonRetrievalException.class))
        .verify();
  }

  @Test
  void sourcePortFailureThatIsAlreadyARetrievalExceptionIsPassedThroughUnchanged() {
    var original = FundComparisonRetrievalException.systemFailure();
    var useCase = useCase(portCommand -> Mono.error(original), referenceDatePort(REFERENCE_DATE));

    StepVerifier.create(useCase.fetchPrices(command(List.of("FAEU"), "10D")))
        .expectErrorSatisfies(error -> assertThat(error).isSameAs(original))
        .verify();
  }

  @Test
  void fetchPricesNeverTouchesTheOptionsOrTheCache() {
    var useCase =
        useCase(
            portCommand -> Mono.just(new RawFundPriceData(List.of())),
            referenceDatePort(REFERENCE_DATE));

    StepVerifier.create(useCase.fetchPrices(command(List.of("FAEU"), "10D")))
        .expectNextCount(1)
        .verifyComplete();

    verifyNoInteractions(optionsUseCase, cache);
  }

  @Test
  void nullCommandThrowsNullPointerException() {
    var useCase =
        useCase(
            portCommand -> Mono.just(new RawFundPriceData(List.of())),
            referenceDatePort(REFERENCE_DATE));

    assertThatThrownBy(() -> useCase.fetchPrices(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("command");
  }

  @Test
  void constructorRequiresEveryDependency() {
    FundComparisonPriceSourcePort sourcePort =
        portCommand -> Mono.just(new RawFundPriceData(List.of()));
    ReferenceDatePort referenceDatePort = referenceDatePort(REFERENCE_DATE);

    assertThatThrownBy(
            () -> new FundComparisonPricesUseCase(null, referenceDatePort, optionsUseCase, cache))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("sourcePort");
    assertThatThrownBy(
            () -> new FundComparisonPricesUseCase(sourcePort, null, optionsUseCase, cache))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("referenceDatePort");
    assertThatThrownBy(
            () -> new FundComparisonPricesUseCase(sourcePort, referenceDatePort, null, cache))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("optionsUseCase");
    assertThatThrownBy(
            () ->
                new FundComparisonPricesUseCase(
                    sourcePort, referenceDatePort, optionsUseCase, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("cache");
  }

  @Test
  void aDownstreamInvalidDateExceptionIsTranslatedToFundComparisonRequestInvalidException() {
    var useCase =
        useCase(
            portCommand -> Mono.error(FundPriceInvalidDateException.forInvalidDate()),
            referenceDatePort(REFERENCE_DATE));

    StepVerifier.create(useCase.fetchPrices(command(List.of("FAEU"), "10D")))
        .expectErrorSatisfies(
            error -> assertThat(error).isInstanceOf(FundComparisonRequestInvalidException.class))
        .verify();
  }
}
