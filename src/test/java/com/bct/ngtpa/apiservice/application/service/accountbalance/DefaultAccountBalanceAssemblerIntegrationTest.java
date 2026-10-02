package com.bct.ngtpa.apiservice.application.service.accountbalance;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.*;
import com.bct.ngtpa.apiservice.application.dto.metadata.*;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class DefaultAccountBalanceAssemblerIntegrationTest {
  @Test
  void assemblesBothRatifiedViewsFromOneNormalizedModel() {
    var raw =
        new RawAccountBalanceData(
            LocalDate.of(2026, 8, 27),
            new RawAccountBalanceDisplayOptions(true, true, true),
            new RawAccountBalanceCurrency(List.of("HKD", "USD"), "HKD"),
            List.of(
                new RawAccountBalanceDisplaySource("EE", "Employee", "僱員", "OE", "Own", "自選", 1)),
            List.of(
                new RawAccountBalanceFundDetail(
                    "F1",
                    "Fund One",
                    "基金一",
                    1,
                    new BigDecimal("10.5000"),
                    "HKD",
                    new BigDecimal("1.0000"),
                    "https://example.test/f1")),
            List.of(
                new RawAccountBalanceHolding(
                    "EE",
                    "OE",
                    "F1",
                    new BigDecimal("2.000"),
                    new BigDecimal("21.00"),
                    new BigDecimal("20.00"),
                    new BigDecimal("1.00"))));
    var assembler = DefaultAccountBalanceAssembler.createForTests(() -> {});
    StepVerifier.create(
            assembler.assemble(
                new GetAccountBalanceCommand(
                    "en", null, "JP", "P", "1", "T", "S", "U", ActorUserRole.ROLE_USER_MBR),
                LocalDate.of(2026, 8, 28),
                metadata(),
                raw))
        .assertNext(
            r -> {
              assertThat(r.investmentPortfolio().fundLevelView()).hasSize(1);
              assertThat(r.investmentPortfolio().subDisplayAccountLevelView()).hasSize(1);
              assertThat(r.accountOverview().displayOptions().vestedBalanceVisible()).isTrue();
              assertThat(r.accountOverview().displayOptions().pendingSwitchInVisible()).isTrue();
              assertThat(r.accountOverview().displayAccounts()).hasSize(1);
              assertThat(r.accountOverview().displayAccounts().getFirst().displayAccountId())
                  .isEqualTo("EE");
              assertThat(r.accountOverview().displayAccounts().getFirst().displayAccountLabel())
                  .isEqualTo("Employee");
              assertThat(r.accountOverview().displayAccounts().getFirst().accountBalance())
                  .isEqualTo("21.00");
              assertThat(r.accountOverview().displayAccounts().getFirst().vestedBalance())
                  .isEqualTo("20.00");
              assertThat(r.accountOverview().accountTotals().totalBalance()).isEqualTo("21.00");
              assertThat(r.accountOverview().accountTotals().pendingSwitchIn()).isEqualTo("1.00");
              assertThat(r.asOfDate()).isEqualTo("27/08/2026");
            })
        .verifyComplete();
  }

  @Test
  void assemblesSuccessfullyWhenTrustHasNoSubDisplayGroupingAtAll() {
    // Reproduces the real jpjpm02 / policy-no 00000000118 / cert-no 0070 payload shape:
    // sub-disp-src, sub-src-desc, and sub-src-chin-desc are ALL blank for every display
    // source and every holding - a legitimate "no sub-grouping" trust, not malformed data.
    var raw =
        new RawAccountBalanceData(
            LocalDate.of(2026, 8, 27),
            new RawAccountBalanceDisplayOptions(true, true, true),
            new RawAccountBalanceCurrency(List.of("HKD"), "HKD"),
            List.of(
                new RawAccountBalanceDisplaySource(
                    "EE", "Employee", "\u50c7\u54e1", "", "", "", 1)),
            List.of(
                new RawAccountBalanceFundDetail(
                    "F1",
                    "Fund One",
                    "\u57fa\u91d1\u4e00",
                    1,
                    new BigDecimal("10.5000"),
                    "HKD",
                    new BigDecimal("1.0000"),
                    "https://example.test/f1")),
            List.of(
                new RawAccountBalanceHolding(
                    "EE",
                    "",
                    "F1",
                    new BigDecimal("2.000"),
                    new BigDecimal("21.00"),
                    new BigDecimal("20.00"),
                    new BigDecimal("1.00"))));
    var assembler = DefaultAccountBalanceAssembler.createForTests(() -> {});

    StepVerifier.create(
            assembler.assemble(
                new GetAccountBalanceCommand(
                    "en", null, "JP", "P", "1", "T", "S", "U", ActorUserRole.ROLE_USER_MBR),
                LocalDate.of(2026, 8, 28),
                metadata(),
                raw))
        .assertNext(
            r -> {
              var fund = r.investmentPortfolio().fundLevelView().getFirst();
              assertThat(
                      fund.funds()
                          .getFirst()
                          .subDisplayAccounts()
                          .getFirst()
                          .subDisplayAccountName())
                  .isEmpty();
              var sub = r.investmentPortfolio().subDisplayAccountLevelView().getFirst();
              assertThat(sub.subDisplayAccounts().getFirst().subDisplayAccountName()).isEmpty();
            })
        .verifyComplete();
  }

  @Test
  void allDisplayOptionsFalseRendersNullForHiddenAmountsAndFxRate() {
    var raw =
        new RawAccountBalanceData(
            LocalDate.of(2026, 8, 27),
            new RawAccountBalanceDisplayOptions(false, false, false),
            new RawAccountBalanceCurrency(List.of("HKD", "USD"), "HKD"),
            List.of(
                new RawAccountBalanceDisplaySource(
                    "EE", "Employee", "\u50c7\u54e1", "OE", "Own", "\u81ea\u9078", 1)),
            List.of(
                new RawAccountBalanceFundDetail(
                    "F1",
                    "Fund One",
                    "\u57fa\u91d1\u4e00",
                    1,
                    new BigDecimal("10.5000"),
                    "HKD",
                    new BigDecimal("1.0000"),
                    "https://example.test/f1")),
            List.of(
                new RawAccountBalanceHolding(
                    "EE",
                    "OE",
                    "F1",
                    new BigDecimal("2.000"),
                    new BigDecimal("21.00"),
                    new BigDecimal("20.00"),
                    new BigDecimal("1.00"))));
    var assembler = DefaultAccountBalanceAssembler.createForTests(() -> {});

    StepVerifier.create(
            assembler.assemble(
                new GetAccountBalanceCommand(
                    "en", null, "JP", "P", "1", "T", "S", "U", ActorUserRole.ROLE_USER_MBR),
                LocalDate.of(2026, 8, 28),
                metadata(),
                raw))
        .assertNext(
            r -> {
              assertThat(r.accountOverview().displayAccounts().getFirst().vestedBalance()).isNull();
              assertThat(r.accountOverview().accountTotals().pendingSwitchIn()).isNull();
              var fund = r.investmentPortfolio().fundLevelView().getFirst().funds().getFirst();
              assertThat(fund.fxRate()).isNull();
              var subFund =
                  r.investmentPortfolio()
                      .subDisplayAccountLevelView()
                      .getFirst()
                      .subDisplayAccounts()
                      .getFirst()
                      .funds()
                      .getFirst();
              assertThat(subFund.fxRate()).isNull();
            })
        .verifyComplete();
  }

  @Test
  void nullCommandFailsTechnically() {
    StepVerifier.create(
            assembler().assemble(null, LocalDate.of(2026, 8, 28), metadata(), validRaw()))
        .expectErrorSatisfies(
            ex ->
                assertThat(ex)
                    .isInstanceOf(AccountBalanceRetrievalException.class)
                    .extracting("reason")
                    .isEqualTo(AccountBalanceRetrievalException.Reason.SYSTEM_FAILURE))
        .verify();
  }

  @Test
  void nullDateFailsTechnically() {
    var command =
        new GetAccountBalanceCommand(
            "en", null, "JP", "P", "1", "T", "S", "U", ActorUserRole.ROLE_USER_MBR);
    StepVerifier.create(assembler().assemble(command, null, metadata(), validRaw()))
        .expectErrorSatisfies(
            ex ->
                assertThat(ex)
                    .isInstanceOf(AccountBalanceRetrievalException.class)
                    .extracting("reason")
                    .isEqualTo(AccountBalanceRetrievalException.Reason.SYSTEM_FAILURE))
        .verify();
  }

  @Test
  void nullMetadataFailsTechnically() {
    var command =
        new GetAccountBalanceCommand(
            "en", null, "JP", "P", "1", "T", "S", "U", ActorUserRole.ROLE_USER_MBR);
    StepVerifier.create(assembler().assemble(command, LocalDate.of(2026, 8, 28), null, validRaw()))
        .expectErrorSatisfies(
            ex ->
                assertThat(ex)
                    .isInstanceOf(AccountBalanceRetrievalException.class)
                    .extracting("reason")
                    .isEqualTo(AccountBalanceRetrievalException.Reason.SYSTEM_FAILURE))
        .verify();
  }

  @Test
  void nullRawDataFailsTechnically() {
    var command =
        new GetAccountBalanceCommand(
            "en", null, "JP", "P", "1", "T", "S", "U", ActorUserRole.ROLE_USER_MBR);
    StepVerifier.create(assembler().assemble(command, LocalDate.of(2026, 8, 28), metadata(), null))
        .expectErrorSatisfies(
            ex ->
                assertThat(ex)
                    .isInstanceOf(AccountBalanceRetrievalException.class)
                    .extracting("reason")
                    .isEqualTo(AccountBalanceRetrievalException.Reason.SYSTEM_FAILURE))
        .verify();
  }

  private static DefaultAccountBalanceAssembler assembler() {
    return DefaultAccountBalanceAssembler.createForTests(() -> {});
  }

  private static RawAccountBalanceData validRaw() {
    return new RawAccountBalanceData(
        LocalDate.of(2026, 8, 27),
        new RawAccountBalanceDisplayOptions(true, true, true),
        new RawAccountBalanceCurrency(List.of("HKD"), "HKD"),
        List.of(
            new RawAccountBalanceDisplaySource(
                "EE", "Employee", "\u50c7\u54e1", "OE", "Own", "\u81ea\u9078", 1)),
        List.of(
            new RawAccountBalanceFundDetail(
                "F1",
                "Fund One",
                "\u57fa\u91d1\u4e00",
                1,
                new BigDecimal("10.5000"),
                "HKD",
                new BigDecimal("1.0000"),
                "https://example.test/f1")),
        List.of(
            new RawAccountBalanceHolding(
                "EE",
                "OE",
                "F1",
                new BigDecimal("2.000"),
                new BigDecimal("21.00"),
                new BigDecimal("20.00"),
                new BigDecimal("1.00"))));
  }

  private static ResolvedGeneralMetadataSet metadata() {
    var s = new GeneralMetadataScope("JP", "T", "P");
    return ResolvedGeneralMetadataSet.normalize(
        s,
        GeneralMetadataResolutionSource.DEFAULT_ALL,
        List.of(
            e(GeneralMetadataName.FUND_PRICE, "4"),
            e(GeneralMetadataName.UNIT_BALANCE, "3"),
            e(GeneralMetadataName.NET_ASSET_VALUE, "2"),
            e(GeneralMetadataName.FX_RATE, "4")));
  }

  private static ResolvedGeneralMetadata e(GeneralMetadataName n, String v) {
    return new ResolvedGeneralMetadata(new GeneralMetadataKey(n, GeneralMetadataType.PRECISION), v);
  }
}
