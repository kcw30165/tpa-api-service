package com.bct.ngtpa.apiservice.application.service.accountgainloss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.AccountGainLossPeriod;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.GetAccountGainLossCommand;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.RawAccountGainLossData;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.RawFundGainLoss;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataKey;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataName;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataResolutionSource;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataScope;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataType;
import com.bct.ngtpa.apiservice.application.dto.metadata.ResolvedGeneralMetadata;
import com.bct.ngtpa.apiservice.application.dto.metadata.ResolvedGeneralMetadataSet;
import com.bct.ngtpa.apiservice.application.exception.AccountGainLossRetrievalException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Branch-oriented tests for {@link AccountGainLossAssembler}. This is a fully self-contained
 * assembler - {@link AccountGainLossPrecisionRenderer} and {@code DynamicDescriptionResolver} are
 * constructed INTERNALLY (round 117 verbatim confirmation), so they run for real, not mocked.
 */
class AccountGainLossAssemblerTest {

  private final AccountGainLossAssembler assembler = new AccountGainLossAssembler();

  private static final GeneralMetadataKey NAV_PRECISION =
      new GeneralMetadataKey(GeneralMetadataName.NET_ASSET_VALUE, GeneralMetadataType.PRECISION);

  private static GetAccountGainLossCommand command(String language) {
    return new GetAccountGainLossCommand(
        language, AccountGainLossPeriod.YEAR_TO_DATE, "env1", "policy1", "cert1", "trust1",
        "SCHEME_A", "actor1", ActorUserRole.ROLE_USER_MBR);
  }

  private static ResolvedGeneralMetadataSet metadataWithPrecision(int precision) {
    ResolvedGeneralMetadata entry =
        new ResolvedGeneralMetadata(NAV_PRECISION, Integer.toString(precision));
    return ResolvedGeneralMetadataSet.normalize(
        new GeneralMetadataScope("env1", "trust1", "policy1"), GeneralMetadataResolutionSource.DEFAULT_ALL, List.of(entry));
  }

  private static ResolvedGeneralMetadataSet metadataWithoutPrecision() {
    return ResolvedGeneralMetadataSet.normalize(
        new GeneralMetadataScope("env1", "trust1", "policy1"), GeneralMetadataResolutionSource.DEFAULT_ALL, List.of());
  }

  private static RawFundGainLoss fund(
      String code, String english, String chinese, int sequence, String gainLoss) {
    return new RawFundGainLoss(code, english, chinese, sequence, new BigDecimal(gainLoss));
  }

  // ----- guards ---------------------------------------------------------------

  @Test
  void nullCommandThrowsSystemFailure() {
    RawAccountGainLossData raw = new RawAccountGainLossData(LocalDate.of(2026, 1, 1), List.of());
    assertThatThrownBy(() -> assembler.assemble(null, metadataWithPrecision(2), raw))
        .isInstanceOf(AccountGainLossRetrievalException.class)
        .satisfies(
            ex ->
                assertThat(((AccountGainLossRetrievalException) ex).reason())
                    .isEqualTo(AccountGainLossRetrievalException.Reason.SYSTEM_FAILURE));
  }

  @Test
  void nullMetadataThrowsSystemFailure() {
    RawAccountGainLossData raw = new RawAccountGainLossData(LocalDate.of(2026, 1, 1), List.of());
    assertThatThrownBy(() -> assembler.assemble(command("en"), null, raw))
        .isInstanceOf(AccountGainLossRetrievalException.class);
  }

  @Test
  void nullRawDataThrowsSystemFailure() {
    assertThatThrownBy(() -> assembler.assemble(command("en"), metadataWithPrecision(2), null))
        .isInstanceOf(AccountGainLossRetrievalException.class);
  }

  @Test
  void nullPriceDateThrowsSystemFailure() {
    RawAccountGainLossData raw = new RawAccountGainLossData(null, List.of());
    assertThatThrownBy(() -> assembler.assemble(command("en"), metadataWithPrecision(2), raw))
        .isInstanceOf(AccountGainLossRetrievalException.class);
  }

  @Test
  void missingPrecisionMetadataThrowsSystemFailure() {
    RawAccountGainLossData raw = new RawAccountGainLossData(LocalDate.of(2026, 1, 1), List.of());
    assertThatThrownBy(
            () -> assembler.assemble(command("en"), metadataWithoutPrecision(), raw))
        .isInstanceOf(AccountGainLossRetrievalException.class)
        .satisfies(
            ex ->
                assertThat(((AccountGainLossRetrievalException) ex).reason())
                    .isEqualTo(AccountGainLossRetrievalException.Reason.SYSTEM_FAILURE));
  }

  // ----- happy path: ordering, summation, rendering, descriptions --------------------

  @Test
  void assembleOrdersFundsBySequenceThenTrimmedCode() {
    RawAccountGainLossData raw =
        new RawAccountGainLossData(
            LocalDate.of(2026, 3, 15),
            List.of(
                fund(" B ", "Bond Fund", "債券基金", 2, "10.00"),
                fund("A", "Equity Fund", "股票基金", 1, "20.00")));
    var result = assembler.assemble(command("en"), metadataWithPrecision(2), raw);
    assertThat(result.funds()).extracting("fundCode").containsExactly("A", "B");
    assertThat(result.asOfDate()).isEqualTo("15/03/2026");
    assertThat(result.period()).isEqualTo(AccountGainLossPeriod.YEAR_TO_DATE);
  }

  @Test
  void assembleSumsGainLossAcrossFunds() {
    RawAccountGainLossData raw =
        new RawAccountGainLossData(
            LocalDate.of(2026, 1, 1),
            List.of(
                fund("A", "Fund A", "基金A", 1, "10.00"), fund("B", "Fund B", "基金B", 2, "5.50")));
    var result = assembler.assemble(command("en"), metadataWithPrecision(2), raw);
    assertThat(result.totalGainLoss()).isEqualTo("15.50");
  }

  @Test
  void assembleResolvesEnglishDescriptionForEnglishLanguage() {
    RawAccountGainLossData raw =
        new RawAccountGainLossData(
            LocalDate.of(2026, 1, 1), List.of(fund("A", "English Name", "中文名稱", 1, "1.00")));
    var result = assembler.assemble(command("en"), metadataWithPrecision(2), raw);
    assertThat(result.funds().get(0).fundName()).isEqualTo("English Name");
  }

  @Test
  void assembleResolvesChineseDescriptionForZhLanguage() {
    RawAccountGainLossData raw =
        new RawAccountGainLossData(
            LocalDate.of(2026, 1, 1), List.of(fund("A", "English Name", "中文名稱", 1, "1.00")));
    var result = assembler.assemble(command("zh"), metadataWithPrecision(2), raw);
    assertThat(result.funds().get(0).fundName()).isEqualTo("中文名稱");
  }

  @Test
  void assembleFallsBackWhenPreferredDescriptionMissing() {
    RawAccountGainLossData raw =
        new RawAccountGainLossData(
            LocalDate.of(2026, 1, 1), List.of(fund("A", "English Only", null, 1, "1.00")));
    var result = assembler.assemble(command("zh"), metadataWithPrecision(2), raw);
    assertThat(result.funds().get(0).fundName()).isEqualTo("English Only");
  }

  @Test
  void assembleWithNoFundsProducesEmptyFundListAndZeroTotal() {
    RawAccountGainLossData raw = new RawAccountGainLossData(LocalDate.of(2026, 1, 1), List.of());
    var result = assembler.assemble(command("en"), metadataWithPrecision(2), raw);
    assertThat(result.funds()).isEmpty();
    assertThat(result.totalGainLoss()).isEqualTo("0.00");
  }

  @Test
  void assembleWithBothDescriptionsMissingThrowsSystemFailure() {
    RawAccountGainLossData raw =
        new RawAccountGainLossData(
            LocalDate.of(2026, 1, 1), List.of(fund("A", null, null, 1, "1.00")));
    assertThatThrownBy(() -> assembler.assemble(command("en"), metadataWithPrecision(2), raw))
        .isInstanceOf(AccountGainLossRetrievalException.class)
        .satisfies(
            ex ->
                assertThat(((AccountGainLossRetrievalException) ex).reason())
                    .isEqualTo(AccountGainLossRetrievalException.Reason.SYSTEM_FAILURE));
  }
}
