package com.bct.ngtpa.apiservice.application.service.accountsummary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.*;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.*;
import com.bct.ngtpa.apiservice.application.dto.accountsummary.AccountSummaryResult;
import com.bct.ngtpa.apiservice.application.dto.accountsummary.GetAccountSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.metadata.*;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountSummaryAssemblerTest {

  private final AccountSummaryAssembler assembler = new AccountSummaryAssembler();

  @Test
  void composesAccountNumberFromPolicyAndCertNo() {
    AccountSummaryResult result =
        assembler.assemble(
            command("00000008802", "0230"),
            balanceResult(),
            gainLossResult(gainLossFund("F1", 1, "5.00")),
            rawBalance(List.of(rawHolding("F1", "EE", "OE", "10.00", "25.0"))),
            metadata());
    assertThat(result.accountNumber()).isEqualTo("00000008802-0230");
  }

  @Test
  void blankPolicyOrCertNoFailsTechnically() {
    assertThatThrownBy(
            () ->
                assembler.assemble(
                    command("", "0230"),
                    balanceResult(),
                    gainLossResult(gainLossFund("F1", 1, "5.00")),
                    rawBalance(List.of(rawHolding("F1", "EE", "OE", "10.00", "25.0"))),
                    metadata()))
        .isInstanceOf(AccountBalanceRetrievalException.class);
  }

  @Test
  void sumsBalanceAndPercentageAcrossMultipleRowsForTheSameFund() {
    var raw =
        rawBalance(
            List.of(
                rawHolding("F1", "EE", "OE", "10.00", "15.0"),
                rawHolding("F1", "EE", "OR", "5.00", "10.0")));
    var result =
        assembler.assemble(
            command("00000008802", "0230"),
            balanceResult(),
            gainLossResult(gainLossFund("F1", 1, "5.00")),
            raw,
            metadata());
    var holding = result.gainLossBreakdown().holdings().getFirst();
    assertThat(holding.balance()).isEqualTo("15.00");
    assertThat(holding.portfolioPercentage()).isEqualTo("25.0");
  }

  @Test
  void soldFundWithNoHoldingRowsRendersZeroRatherThanNull() {
    var result =
        assembler.assemble(
            command("00000008802", "0230"),
            balanceResult(),
            gainLossResult(gainLossFund("SOLD", 1, "412.30")),
            rawBalance(List.of()),
            metadata());
    var holding = result.gainLossBreakdown().holdings().getFirst();
    assertThat(holding.balance()).isEqualTo("0.00");
    assertThat(holding.portfolioPercentage()).isEqualTo("0.0");
    assertThat(holding.gainLoss()).isEqualTo("412.30");
  }

  @Test
  void fundWithEveryRowUnreportedYieldsNullPercentageNotZero() {
    var raw = rawBalance(List.of(rawHolding("F1", "EE", "OE", "10.00", null)));
    var result =
        assembler.assemble(
            command("00000008802", "0230"),
            balanceResult(),
            gainLossResult(gainLossFund("F1", 1, "5.00")),
            raw,
            metadata());
    var holding = result.gainLossBreakdown().holdings().getFirst();
    assertThat(holding.balance()).isEqualTo("10.00");
    assertThat(holding.portfolioPercentage()).isNull();
  }

  @Test
  void fundWithSomeRowsUnreportedAndSomeReportedFailsTechnically() {
    var raw =
        rawBalance(
            List.of(
                rawHolding("F1", "EE", "OE", "10.00", "25.0"),
                rawHolding("F1", "EE", "OR", "5.00", null)));
    assertThatThrownBy(
            () ->
                assembler.assemble(
                    command("00000008802", "0230"),
                    balanceResult(),
                    gainLossResult(gainLossFund("F1", 1, "5.00")),
                    raw,
                    metadata()))
        .isInstanceOf(AccountBalanceRetrievalException.class);
  }

  @Test
  void heldFundAbsentFromGainLossListViolatesTheSupersetInvariant() {
    var raw = rawBalance(List.of(rawHolding("F1", "EE", "OE", "10.00", "25.0")));
    assertThatThrownBy(
            () ->
                assembler.assemble(
                    command("00000008802", "0230"),
                    balanceResult(),
                    gainLossResult(gainLossFund("OTHER", 1, "5.00")),
                    raw,
                    metadata()))
        .isInstanceOf(AccountBalanceRetrievalException.class);
  }

  @Test
  void nonParticipatingNavSentinelFailsTechnically() {
    var raw = rawBalance(List.of(rawHolding("F1", "EE", "OE", "-1", "25.0")));
    assertThatThrownBy(
            () ->
                assembler.assemble(
                    command("00000008802", "0230"),
                    balanceResult(),
                    gainLossResult(gainLossFund("F1", 1, "5.00")),
                    raw,
                    metadata()))
        .isInstanceOf(AccountBalanceRetrievalException.class);
  }

  @Test
  void totalValueAndTotalGainLossAreReusedRatherThanRecomputed() {
    var result =
        assembler.assemble(
            command("00000008802", "0230"),
            balanceResult(),
            gainLossResult(gainLossFund("F1", 1, "5.00")),
            rawBalance(List.of(rawHolding("F1", "EE", "OE", "10.00", "25.0"))),
            metadata());
    assertThat(result.gainLossBreakdown().totalValue()).isEqualTo("100.00");
    assertThat(result.gainLossBreakdown().totalGainLoss()).isEqualTo("9.99");
  }

  @Test
  void holdingsFollowTheGainLossOrderingNotBalanceOrdering() {
    var raw =
        rawBalance(
            List.of(
                rawHolding("F2", "EE", "OE", "1.00", "1.0"),
                rawHolding("F1", "EE", "OE", "2.00", "2.0")));
    var result =
        assembler.assemble(
            command("00000008802", "0230"),
            balanceResult(),
            gainLossResult(gainLossFund("F1", 1, "1.00"), gainLossFund("F2", 2, "2.00")),
            raw,
            metadata());
    assertThat(result.gainLossBreakdown().holdings())
        .extracting("fundCode")
        .containsExactly("F1", "F2");
  }

  @Test
  void fundNamePrefersTheBalanceSideFundDetailWhenTheFundIsHeld() {
    var raw =
        new RawAccountBalanceData(
            LocalDate.of(2026, 8, 31),
            new RawAccountBalanceDisplayOptions(false, false, false),
            new RawAccountBalanceCurrency(List.of("HKD"), "HKD"),
            List.of(),
            List.of(fundDetail("F1", "Balance Side Name", "餘額方名稱")),
            List.of(rawHolding("F1", "EE", "OE", "10.00", "25.0")));
    var result =
        assembler.assemble(
            command("00000008802", "0230"),
            balanceResult(),
            gainLossResult(gainLossFund("F1", 1, "5.00", "Gain Loss Side Name")),
            raw,
            metadata());
    assertThat(result.gainLossBreakdown().holdings().getFirst().fundName())
        .isEqualTo("Balance Side Name");
  }

  @Test
  void fundNameFallsBackToTheAlreadyResolvedGainLossNameWhenNotHeld() {
    var result =
        assembler.assemble(
            command("00000008802", "0230"),
            balanceResult(),
            gainLossResult(gainLossFund("SOLD", 1, "5.00", "Gain Loss Side Name")),
            rawBalance(List.of()),
            metadata());
    assertThat(result.gainLossBreakdown().holdings().getFirst().fundName())
        .isEqualTo("Gain Loss Side Name");
  }

  // --- fixtures ---------------------------------------------------------

  private static GetAccountSummaryCommand command(String policyNo, String certNo) {
    return new GetAccountSummaryCommand(
        "en",
        null,
        AccountGainLossPeriod.SINCE_SCHEME_INCEPTION,
        "JP",
        policyNo,
        certNo,
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

  private static AccountBalanceResult balanceResult() {
    return new AccountBalanceResult(
        "31/08/2026",
        new CurrencySelection("HKD", List.of("HKD")),
        new AccountOverview(
            new AccountOverviewDisplayOptions(false, false),
            List.of(),
            new AccountOverviewTotals("100.00", null)),
        new InvestmentPortfolio(List.of(), List.of()));
  }

  private static RawAccountBalanceData rawBalance(List<RawAccountBalanceHolding> holdings) {
    return new RawAccountBalanceData(
        LocalDate.of(2026, 8, 31),
        new RawAccountBalanceDisplayOptions(false, false, false),
        new RawAccountBalanceCurrency(List.of("HKD"), "HKD"),
        List.of(),
        List.of(),
        holdings);
  }

  private static RawAccountBalanceHolding rawHolding(
      String fundCode,
      String displaySource,
      String subDisplaySource,
      String nav,
      String percentage) {
    return new RawAccountBalanceHolding(
        displaySource,
        subDisplaySource,
        fundCode,
        BigDecimal.ONE,
        new BigDecimal(nav),
        null,
        null,
        percentage == null ? null : new BigDecimal(percentage));
  }

  private static RawAccountBalanceFundDetail fundDetail(
      String fundCode, String name, String chineseName) {
    return new RawAccountBalanceFundDetail(
        fundCode, name, chineseName, 1, BigDecimal.ONE, "HKD", BigDecimal.ONE, null, null);
  }

  private static AccountGainLossResult gainLossResult(AccountGainLossFund... funds) {
    return new AccountGainLossResult(
        AccountGainLossPeriod.SINCE_SCHEME_INCEPTION, "31/08/2026", "9.99", List.of(funds));
  }

  private static AccountGainLossFund gainLossFund(String fundCode, int sequence, String gainLoss) {
    return gainLossFund(fundCode, sequence, gainLoss, "Fallback Name");
  }

  private static AccountGainLossFund gainLossFund(
      String fundCode, int sequence, String gainLoss, String name) {
    return new AccountGainLossFund(fundCode, name, sequence, gainLoss);
  }
}
