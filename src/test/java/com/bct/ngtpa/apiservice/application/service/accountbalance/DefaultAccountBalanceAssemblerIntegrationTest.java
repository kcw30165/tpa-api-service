package com.bct.ngtpa.apiservice.application.service.accountbalance;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.*;
import com.bct.ngtpa.apiservice.application.dto.metadata.*;
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
