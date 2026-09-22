package com.bct.ngtpa.apiservice.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.*;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.*;
import com.bct.ngtpa.apiservice.application.dto.accountsummary.GetAccountSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.metadata.*;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import com.bct.ngtpa.apiservice.application.exception.AccountGainLossRetrievalException;
import com.bct.ngtpa.apiservice.application.port.in.AccountBalanceAssembler;
import com.bct.ngtpa.apiservice.application.port.in.GeneralMetadataResolver;
import com.bct.ngtpa.apiservice.application.port.out.AccountBalanceSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.AccountGainLossSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.service.accountgainloss.AccountGainLossAssembler;
import com.bct.ngtpa.apiservice.application.service.accountsummary.AccountSummaryAssembler;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GetAccountSummaryServiceTest {

  private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 8, 31);

  @Test
  void resolvesReferenceDateAndMetadataOnceThenCallsBalanceBeforeGainLoss() {
    var callOrder = new ArrayList<String>();
    var capturedQuery = new AtomicReference<ResolveGeneralMetadataQuery>();
    var referenceDateCalls = new AtomicInteger();
    var metadataCalls = new AtomicInteger();

    ReferenceDatePort referenceDatePort =
        accountEnv -> {
          referenceDateCalls.incrementAndGet();
          return Mono.just(REFERENCE_DATE);
        };
    GeneralMetadataResolver metadataResolver =
        query -> {
          metadataCalls.incrementAndGet();
          capturedQuery.set(query);
          return Mono.just(metadata());
        };
    AccountBalanceSourcePort balanceSource =
        fetch -> {
          callOrder.add("balance");
          return Mono.just(rawBalance());
        };
    AccountGainLossSourcePort gainLossSource =
        fetch -> {
          callOrder.add("gainloss");
          return Mono.just(rawGainLoss());
        };
    AccountBalanceAssembler balanceAssembler =
        (command, referenceDate, resolvedMetadata, raw) -> Mono.just(balanceResult());

    var service =
        service(
            referenceDatePort, metadataResolver, balanceSource, gainLossSource, balanceAssembler);

    StepVerifier.create(service.get(command()))
        .assertNext(
            result -> {
              assertThat(result.accountNumber()).isEqualTo("00000008802-0230");
              assertThat(result.gainLossBreakdown().holdings()).hasSize(1);
            })
        .verifyComplete();

    assertThat(referenceDateCalls.get()).isEqualTo(1);
    assertThat(metadataCalls.get()).isEqualTo(1);
    assertThat(callOrder).containsExactly("balance", "gainloss");
    assertThat(capturedQuery.get().requiredKeys())
        .contains(
            new GeneralMetadataKey(
                GeneralMetadataName.AB_PERCENTAGE, GeneralMetadataType.PRECISION));
  }

  @Test
  void gainLossIsNeverInvokedWhenBalanceFails() {
    var gainLossInvoked = new AtomicBoolean(false);
    ReferenceDatePort referenceDatePort = accountEnv -> Mono.just(REFERENCE_DATE);
    GeneralMetadataResolver metadataResolver = query -> Mono.just(metadata());
    AccountBalanceSourcePort balanceSource =
        fetch -> Mono.error(AccountBalanceRetrievalException.accountNotFound());
    AccountGainLossSourcePort gainLossSource =
        fetch -> {
          gainLossInvoked.set(true);
          return Mono.just(rawGainLoss());
        };
    AccountBalanceAssembler balanceAssembler =
        (command, referenceDate, resolvedMetadata, raw) -> Mono.just(balanceResult());

    var service =
        service(
            referenceDatePort, metadataResolver, balanceSource, gainLossSource, balanceAssembler);

    StepVerifier.create(service.get(command()))
        .expectErrorSatisfies(
            ex -> {
              assertThat(ex).isInstanceOf(AccountBalanceRetrievalException.class);
              assertThat(((AccountBalanceRetrievalException) ex).reason())
                  .isEqualTo(AccountBalanceRetrievalException.Reason.ACCOUNT_NOT_FOUND);
            })
        .verify();
    assertThat(gainLossInvoked.get()).isFalse();
  }

  @Test
  void gainLossFailureAfterBalanceSuccessPropagatesUnchanged() {
    ReferenceDatePort referenceDatePort = accountEnv -> Mono.just(REFERENCE_DATE);
    GeneralMetadataResolver metadataResolver = query -> Mono.just(metadata());
    AccountBalanceSourcePort balanceSource = fetch -> Mono.just(rawBalance());
    AccountGainLossSourcePort gainLossSource =
        fetch -> Mono.error(AccountGainLossRetrievalException.fundSwitchingInProgress());
    AccountBalanceAssembler balanceAssembler =
        (command, referenceDate, resolvedMetadata, raw) -> Mono.just(balanceResult());

    var service =
        service(
            referenceDatePort, metadataResolver, balanceSource, gainLossSource, balanceAssembler);

    StepVerifier.create(service.get(command()))
        .expectErrorSatisfies(
            ex -> {
              assertThat(ex).isInstanceOf(AccountGainLossRetrievalException.class);
              assertThat(((AccountGainLossRetrievalException) ex).reason())
                  .isEqualTo(AccountGainLossRetrievalException.Reason.FUND_SWITCHING_IN_PROGRESS);
            })
        .verify();
  }

  @Test
  void unexpectedRuntimeExceptionMapsToAccountBalanceSystemFailure() {
    ReferenceDatePort referenceDatePort =
        accountEnv -> Mono.error(new IllegalStateException("boom"));
    GeneralMetadataResolver metadataResolver = query -> Mono.just(metadata());
    AccountBalanceSourcePort balanceSource = fetch -> Mono.just(rawBalance());
    AccountGainLossSourcePort gainLossSource = fetch -> Mono.just(rawGainLoss());
    AccountBalanceAssembler balanceAssembler =
        (command, referenceDate, resolvedMetadata, raw) -> Mono.just(balanceResult());

    var service =
        service(
            referenceDatePort, metadataResolver, balanceSource, gainLossSource, balanceAssembler);

    StepVerifier.create(service.get(command()))
        .expectErrorSatisfies(
            ex -> {
              assertThat(ex).isInstanceOf(AccountBalanceRetrievalException.class);
              assertThat(((AccountBalanceRetrievalException) ex).reason())
                  .isEqualTo(AccountBalanceRetrievalException.Reason.SYSTEM_FAILURE);
            })
        .verify();
  }

  // --- fixtures ---------------------------------------------------------

  private static GetAccountSummaryService service(
      ReferenceDatePort referenceDatePort,
      GeneralMetadataResolver metadataResolver,
      AccountBalanceSourcePort balanceSource,
      AccountGainLossSourcePort gainLossSource,
      AccountBalanceAssembler balanceAssembler) {
    return new GetAccountSummaryService(
        referenceDatePort,
        metadataResolver,
        balanceSource,
        gainLossSource,
        balanceAssembler,
        new AccountGainLossAssembler(),
        new AccountSummaryAssembler());
  }

  private static GetAccountSummaryCommand command() {
    return new GetAccountSummaryCommand(
        "en",
        null,
        AccountGainLossPeriod.SINCE_SCHEME_INCEPTION,
        "JP",
        "00000008802",
        "0230",
        "T",
        "S",
        "U",
        ActorUserRole.ROLE_USER_MBR);
  }

  private static ResolvedGeneralMetadataSet metadata() {
    var scope = new GeneralMetadataScope("JP", "T", "00000008802");
    return ResolvedGeneralMetadataSet.normalize(
        scope,
        GeneralMetadataResolutionSource.DEFAULT_ALL,
        List.of(
            new ResolvedGeneralMetadata(
                new GeneralMetadataKey(
                    GeneralMetadataName.NET_ASSET_VALUE, GeneralMetadataType.PRECISION),
                "2"),
            new ResolvedGeneralMetadata(
                new GeneralMetadataKey(
                    GeneralMetadataName.AB_PERCENTAGE, GeneralMetadataType.PRECISION),
                "1")));
  }

  private static RawAccountBalanceData rawBalance() {
    return new RawAccountBalanceData(
        REFERENCE_DATE,
        new RawAccountBalanceDisplayOptions(false, false, false),
        new RawAccountBalanceCurrency(List.of("HKD"), "HKD"),
        List.of(),
        List.of(),
        List.of(
            new RawAccountBalanceHolding(
                "EE",
                "OE",
                "F1",
                BigDecimal.ONE,
                new BigDecimal("10.00"),
                null,
                null,
                new BigDecimal("25.0"))));
  }

  private static RawAccountGainLossData rawGainLoss() {
    return new RawAccountGainLossData(
        REFERENCE_DATE,
        List.of(new RawFundGainLoss("F1", "Fund One", "基金一", 1, new BigDecimal("5.00"))));
  }

  private static AccountBalanceResult balanceResult() {
    return new AccountBalanceResult(
        "31/08/2026",
        new CurrencySelection("HKD", List.of("HKD")),
        new AccountOverview(
            new AccountOverviewDisplayOptions(false, false),
            List.of(),
            new AccountOverviewTotals("10.00", null)),
        new InvestmentPortfolio(List.of(), List.of()));
  }
}
