package com.bct.ngtpa.apiservice.adapter.in.web.accountsummary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.AccountGainLossPeriod;
import com.bct.ngtpa.apiservice.application.dto.accountsummary.AccountSummaryResult;
import com.bct.ngtpa.apiservice.application.dto.accountsummary.GainLossBreakdown;
import com.bct.ngtpa.apiservice.application.dto.accountsummary.GainLossBreakdownHolding;
import com.bct.ngtpa.apiservice.application.dto.accountsummary.GetAccountSummaryCommand;
import com.bct.ngtpa.apiservice.application.port.in.GetAccountSummaryUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * Note: this test binds directly to the controller instance via {@code WebTestClient.
 * bindToController(...)}, which does NOT include the shared X-Request-Id enforcement filter (that
 * is registered at the application context level, not per-controller). Missing-header rejection is
 * therefore covered by the filter's own tests, not duplicated here.
 */
class AccountSummaryControllerTest {

  @Test
  void defaultsPeriodToSinceSchemeInceptionAndCurrencyToNull() {
    var captor = ArgumentCaptor.forClass(GetAccountSummaryCommand.class);
    GetAccountSummaryUseCase useCase = Mockito.mock(GetAccountSummaryUseCase.class);
    when(useCase.get(captor.capture())).thenReturn(Mono.just(result()));

    client(useCase, resolver(context()))
        .get()
        .uri("/api/v1/account/summary")
        .header("X-Request-Id", "req-1")
        .exchange()
        .expectStatus()
        .isOk();

    assertThat(captor.getValue().period()).isEqualTo(AccountGainLossPeriod.SINCE_SCHEME_INCEPTION);
    assertThat(captor.getValue().currency()).isNull();
  }

  @Test
  void blankCurrencyIsCleanedToNullExactlyLikeAccountBalance() {
    var captor = ArgumentCaptor.forClass(GetAccountSummaryCommand.class);
    GetAccountSummaryUseCase useCase = Mockito.mock(GetAccountSummaryUseCase.class);
    when(useCase.get(captor.capture())).thenReturn(Mono.just(result()));

    client(useCase, resolver(context()))
        .get()
        .uri("/api/v1/account/summary?currency=")
        .header("X-Request-Id", "req-2")
        .exchange()
        .expectStatus()
        .isOk();

    assertThat(captor.getValue().currency()).isNull();
  }

  @Test
  void selectedCurrencyAndPeriodAreForwarded() {
    var captor = ArgumentCaptor.forClass(GetAccountSummaryCommand.class);
    GetAccountSummaryUseCase useCase = Mockito.mock(GetAccountSummaryUseCase.class);
    when(useCase.get(captor.capture())).thenReturn(Mono.just(result()));

    client(useCase, resolver(context()))
        .get()
        .uri("/api/v1/account/summary?currency=USD&period=YEAR_TO_DATE")
        .header("X-Request-Id", "req-3")
        .exchange()
        .expectStatus()
        .isOk();

    assertThat(captor.getValue().currency()).isEqualTo("USD");
    assertThat(captor.getValue().period()).isEqualTo(AccountGainLossPeriod.YEAR_TO_DATE);
  }

  @Test
  void selectedAccountContextIsForwardedIntoTheCommand() {
    var captor = ArgumentCaptor.forClass(GetAccountSummaryCommand.class);
    GetAccountSummaryUseCase useCase = Mockito.mock(GetAccountSummaryUseCase.class);
    when(useCase.get(captor.capture())).thenReturn(Mono.just(result()));

    client(useCase, resolver(context()))
        .get()
        .uri("/api/v1/account/summary")
        .header("X-Request-Id", "req-4")
        .exchange()
        .expectStatus()
        .isOk();

    var command = captor.getValue();
    assertThat(command.accountEnv()).isEqualTo("JP");
    assertThat(command.policyNo()).isEqualTo("00000008802");
    assertThat(command.certNo()).isEqualTo("0230");
    assertThat(command.actorUserId()).isEqualTo("U");
    assertThat(command.actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
  }

  @Test
  void responseUsesResultDataWithAccountNumberFirstAndBreakdownLast() {
    client(c -> Mono.just(result()), resolver(context()))
        .get()
        .uri("/api/v1/account/summary")
        .header("X-Request-Id", "req-5")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.result.data.accountNumber")
        .isEqualTo("00000008802-0230")
        .jsonPath("$.result.data.asOfDate")
        .isEqualTo("31/08/2026")
        .jsonPath("$.result.data.gainLossBreakdown.period")
        .isEqualTo("SINCE_SCHEME_INCEPTION")
        .jsonPath("$.result.data.gainLossBreakdown.totalValue")
        .isEqualTo("100.00")
        .jsonPath("$.result.data.gainLossBreakdown.totalGainLoss")
        .isEqualTo("9.99")
        .jsonPath("$.result.data.gainLossBreakdown.holdings[0].fundCode")
        .isEqualTo("F1")
        .jsonPath("$.result.data.gainLossBreakdown.holdings[0].balance")
        .isEqualTo("10.00")
        .jsonPath("$.result.data.gainLossBreakdown.holdings[0].portfolioPercentage")
        .isEqualTo("25.0");
  }

  private static AccountSummaryResult result() {
    return new AccountSummaryResult(
        "00000008802-0230",
        "31/08/2026",
        new CurrencySelection("HKD", List.of("HKD")),
        new AccountOverview(
            new AccountOverviewDisplayOptions(false, false),
            List.of(),
            new AccountOverviewTotals("100.00", null)),
        new InvestmentPortfolio(List.of(), List.of()),
        new GainLossBreakdown(
            "SINCE_SCHEME_INCEPTION",
            "31/08/2026",
            "100.00",
            "9.99",
            List.of(new GainLossBreakdownHolding("F1", "Fund One", 1, "10.00", "25.0", "5.00"))));
  }

  private static PortalAccessContext context() {
    return new PortalAccessContext(
        new ActorContext("U", ActorUserRole.ROLE_USER_MBR),
        new AccountContext("ACC-1", "JP", "00000008802", "0230", "T", "S", TermStatus.BLANK, null));
  }

  private static CurrentPortalAccessContextResolver resolver(PortalAccessContext context) {
    return new CurrentPortalAccessContextResolver() {
      @Override
      public Mono<PortalAccessContext> current() {
        return Mono.just(context);
      }

      @Override
      public Mono<PortalAccessContext> currentOrEmpty() {
        return Mono.just(context);
      }
    };
  }

  private static WebTestClient client(
      GetAccountSummaryUseCase useCase, CurrentPortalAccessContextResolver resolver) {
    var controller = new AccountSummaryController(useCase, resolver);
    return WebTestClient.bindToController(controller).build();
  }
}
