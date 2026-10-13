package com.bct.ngtpa.apiservice.application.service.accountbalance;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.AccountBalanceResult;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.GetAccountBalanceCommand;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.RawAccountBalanceCurrency;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.RawAccountBalanceData;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.RawAccountBalanceDisplayOptions;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.RawAccountBalanceDisplaySource;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.RawAccountBalanceFundDetail;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.RawAccountBalanceHolding;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataKey;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataName;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataResolutionSource;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataScope;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataType;
import com.bct.ngtpa.apiservice.application.dto.metadata.ResolvedGeneralMetadata;
import com.bct.ngtpa.apiservice.application.dto.metadata.ResolvedGeneralMetadataSet;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Factsheet selection for both portfolio views: zh_HK prefers chin-fund-fact-sheet and falls back
 * to fund-fact-sheet when the Chinese link is absent, blank or malformed; every other language uses
 * fund-fact-sheet only. Each malformed candidate is reported once per view.
 */
class AccountBalanceChineseFactsheetSelectionTest {

  private static final String EN = "https://example.test/en.pdf";
  private static final String ZH = "https://example.test/zh.pdf";

  private final AtomicInteger malformed = new AtomicInteger();

  @Test
  void englishUsesTheEnglishLinkAndNeverInspectsTheChineseLink() {
    assertUrls(assemble("en", EN, "/relative"), EN);
    assertThat(malformed).hasValue(0);
  }

  @Test
  void englishNeverFallsBackToTheChineseLink() {
    assertUrls(assemble("en", null, ZH), null);
    assertThat(malformed).hasValue(0);
  }

  @Test
  void chineseUsesTheChineseLink() {
    assertUrls(assemble("zh_HK", EN, ZH), ZH);
    assertThat(malformed).hasValue(0);
  }

  @Test
  void chineseFallsBackToTheEnglishLinkWhenTheChineseLinkIsNullEmptyOrBlank() {
    assertUrls(assemble("zh_HK", EN, null), EN);
    assertUrls(assemble("zh_HK", EN, ""), EN);
    assertUrls(assemble("zh_HK", EN, "   "), EN);
    assertThat(malformed).hasValue(0);
  }

  @Test
  void chineseFallsBackToTheEnglishLinkWhenTheChineseLinkIsMalformedAndReportsIt() {
    assertUrls(assemble("zh_HK", EN, "/relative"), EN);
    assertThat(malformed).hasValue(2);
  }

  @Test
  void chineseOmitsTheLinkWhenBothLinksAreMalformed() {
    assertUrls(assemble("zh_HK", "not a url", "/relative"), null);
    assertThat(malformed).hasValue(4);
  }

  @Test
  void chineseOmitsTheLinkWhenBothLinksAreAbsent() {
    assertUrls(assemble("zh_HK", null, null), null);
    assertThat(malformed).hasValue(0);
  }

  private AccountBalanceResult assemble(String language, String english, String chinese) {
    var raw =
        new RawAccountBalanceData(
            LocalDate.of(2026, 8, 27),
            new RawAccountBalanceDisplayOptions(true, true, true),
            new RawAccountBalanceCurrency(List.of("HKD"), "HKD"),
            List.of(
                new RawAccountBalanceDisplaySource(
                    "EE", "Employee", "Employee ZH", "OE", "Own", "Own ZH", 1)),
            List.of(
                new RawAccountBalanceFundDetail(
                    "F1",
                    "Fund One",
                    "Fund One ZH",
                    1,
                    new BigDecimal("10.5000"),
                    "HKD",
                    new BigDecimal("1.0000"),
                    english,
                    chinese)),
            List.of(
                new RawAccountBalanceHolding(
                    "EE",
                    "OE",
                    "F1",
                    new BigDecimal("2.000"),
                    new BigDecimal("21.00"),
                    new BigDecimal("20.00"),
                    new BigDecimal("1.00"))));
    return DefaultAccountBalanceAssembler.createForTests(malformed::incrementAndGet)
        .assemble(
            new GetAccountBalanceCommand(
                language, null, "JP", "P", "1", "T", "S", "U", ActorUserRole.ROLE_USER_MBR),
            LocalDate.of(2026, 8, 28),
            metadata(),
            raw)
        .block();
  }

  private static void assertUrls(AccountBalanceResult result, String expected) {
    var portfolio = result.investmentPortfolio();
    assertThat(portfolio.fundLevelView().getFirst().funds().getFirst().factsheetUrl())
        .isEqualTo(expected);
    assertThat(
            portfolio
                .subDisplayAccountLevelView()
                .getFirst()
                .subDisplayAccounts()
                .getFirst()
                .funds()
                .getFirst()
                .factsheetUrl())
        .isEqualTo(expected);
  }

  private static ResolvedGeneralMetadataSet metadata() {
    return ResolvedGeneralMetadataSet.normalize(
        new GeneralMetadataScope("JP", "T", "P"),
        GeneralMetadataResolutionSource.DEFAULT_ALL,
        List.of(
            precision(GeneralMetadataName.FUND_PRICE, "4"),
            precision(GeneralMetadataName.UNIT_BALANCE, "3"),
            precision(GeneralMetadataName.NET_ASSET_VALUE, "2"),
            precision(GeneralMetadataName.FX_RATE, "4")));
  }

  private static ResolvedGeneralMetadata precision(GeneralMetadataName name, String value) {
    return new ResolvedGeneralMetadata(
        new GeneralMetadataKey(name, GeneralMetadataType.PRECISION), value);
  }
}
