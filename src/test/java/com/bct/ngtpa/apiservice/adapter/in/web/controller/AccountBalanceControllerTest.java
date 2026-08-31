package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.accountbalance.AccountBalanceController;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequiredRequestIdWebFilter;
import com.bct.ngtpa.apiservice.adapter.in.web.response.accountbalance.*;
import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.port.in.GetAccountBalanceUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class AccountBalanceControllerTest {
  @Test
  void missingRequestIdUsesSharedFilter() {
    client(c -> Mono.just(result()), resolver(context()))
        .get()
        .uri("/api/v1/account-balance")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("VALIDATION_FAILED")
        .jsonPath("$.errors[0].targets[0]")
        .isEqualTo("X-Request-Id");
  }

  @Test
  void missingLanguageAndCurrencyAreValidAndDefaultToEnglish() {
    AtomicReference<GetAccountBalanceCommand> captured = new AtomicReference<>();
    client(
            c -> {
              captured.set(c);
              return Mono.just(result());
            },
            resolver(context()))
        .get()
        .uri("/api/v1/account-balance")
        .header("X-Request-Id", "req-1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.result.data.asOfDate")
        .isEqualTo("28/08/2026")
        .jsonPath("$.result.data.accountOverview.vestedBalance")
        .value(value -> assertThat(value).isNull());
    assertThat(captured.get().language()).isEqualTo("en");
    assertThat(captured.get().currency()).isNull();
  }

  @Test
  void suppliedCurrencyAndTrustedContextReachUseCaseWithoutIdempotencyKey() {
    AtomicReference<GetAccountBalanceCommand> captured = new AtomicReference<>();
    client(
            c -> {
              captured.set(c);
              return Mono.just(result());
            },
            resolver(context()))
        .get()
        .uri("/api/v1/account-balance?currency=USD")
        .header("X-Request-Id", "req-2")
        .exchange()
        .expectStatus()
        .isOk();
    assertThat(captured.get().currency()).isEqualTo("USD");
    assertThat(captured.get().accountEnv()).isEqualTo("JP");
    assertThat(captured.get().policyNo()).isEqualTo("POL-1");
    assertThat(captured.get().certNo()).isEqualTo("CERT-1");
    assertThat(captured.get().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
  }

  @Test
  void responseUsesResultDataAndApprovedTerminologyWithScopedOmission() {
    client(c -> Mono.just(result()), resolver(context()))
        .get()
        .uri("/api/v1/account-balance")
        .header("X-Request-Id", "req-3")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.result.data.currencySelection.selectedCurrency")
        .isEqualTo("HKD")
        .jsonPath("$.result.data.accountOverview.accountBalance")
        .isEqualTo("100.00")
        .jsonPath("$.result.data.investmentPortfolio.fundLevelView[0].displayAccountName")
        .isEqualTo("Display Account")
        .jsonPath("$.result.data.investmentPortfolio.fundLevelView[0].funds[0].fxRate")
        .doesNotExist();
  }

  private static WebTestClient client(
      GetAccountBalanceUseCase useCase, CurrentPortalAccessContextResolver resolver) {
    return WebTestClient.bindToController(new AccountBalanceController(useCase, resolver))
        .webFilter(new RequiredRequestIdWebFilter(new ObjectMapper()))
        .build();
  }

  private static CurrentPortalAccessContextResolver resolver(PortalAccessContext c) {
    return new CurrentPortalAccessContextResolver() {
      public Mono<PortalAccessContext> current() {
        return Mono.just(c);
      }

      public Mono<PortalAccessContext> currentOrEmpty() {
        return Mono.just(c);
      }
    };
  }

  private static PortalAccessContext context() {
    return new PortalAccessContext(
        new ActorContext("user-1", ActorUserRole.ROLE_USER_MBR),
        new AccountContext("ACC-1", "JP", "POL-1", "CERT-1", "JPM", "OE", TermStatus.BLANK, null));
  }

  private static AccountBalanceResult result() {
    var leaf =
        new FundLevelSubDisplayAccount(
            "Sub-Display-Account", "OE", "1.000", "100.00", "10.0000", null, null);
    var fund =
        new FundLevelFund("F1", "Fund A", "10.0000", "1.000", "100.00", null, null, List.of(leaf));
    var fundView = new FundLevelDisplayAccount("Display Account", "EE", 1, List.of(fund));
    var subFund = new SubDisplayLevelFund("F1", "Fund A", "10.0000", "1.000", "100.00", null, null);
    var sub = new SubDisplayLevelSubDisplayAccount("Sub-Display-Account", "OE", List.of(subFund));
    var subView = new SubDisplayLevelDisplayAccount("Display Account", "EE", 1, List.of(sub));
    return new AccountBalanceResult(
        "28/08/2026",
        new CurrencySelection("HKD", List.of("HKD", "USD")),
        new AccountOverview("100.00", null, null),
        new InvestmentPortfolio(List.of(fundView), List.of(subView)));
  }
}
