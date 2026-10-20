package com.bct.ngtpa.apiservice.application.dto.fundcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData.RawFundComparisonOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Fund Comparison M5.2: the session options cache entry (09 section 9.5.2). */
class FundComparisonOptionsCacheEntryTest {

  private static final String ACCOUNT_REF = "acc_abcdefghijklmnopqrstuvwx";
  private static final String POLICY_NO = "00000008802";

  private static FundComparisonOptionsCacheEntry entry(List<String> fundCodes) {
    return new FundComparisonOptionsCacheEntry(ACCOUNT_REF, POLICY_NO, "10D,1M", fundCodes);
  }

  @Test
  void keepsValuesVerbatim() {
    var entry = entry(List.of("FAEU", "FBAU"));

    assertThat(entry.accountRef()).isEqualTo(ACCOUNT_REF);
    assertThat(entry.policyNo()).isEqualTo(POLICY_NO);
    assertThat(entry.rawTimeRange()).isEqualTo("10D,1M");
    assertThat(entry.fundCodes()).containsExactly("FAEU", "FBAU");
  }

  @Test
  void ofTakesTheRawTimeRangeAndFundCodesInDownstreamOrder() {
    var raw =
        new RawFundComparisonOptionsData(
            "10D,1M,3Y",
            List.of(
                new RawFundComparisonOption("FBAU", "B Fund", ""),
                new RawFundComparisonOption("FAEU", "A Fund", "")));

    var entry = FundComparisonOptionsCacheEntry.of(ACCOUNT_REF, POLICY_NO, raw);

    assertThat(entry.rawTimeRange()).isEqualTo("10D,1M,3Y");
    assertThat(entry.fundCodes()).containsExactly("FBAU", "FAEU");
  }

  @Test
  void ofRejectsNullRawData() {
    assertThatThrownBy(() -> FundComparisonOptionsCacheEntry.of(ACCOUNT_REF, POLICY_NO, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void belongsToRequiresBothAccountRefAndPolicyNo() {
    var entry = entry(List.of("FAEU"));

    assertThat(entry.belongsTo(ACCOUNT_REF, POLICY_NO)).isTrue();
    assertThat(entry.belongsTo("acc_otherotherotherotherother", POLICY_NO)).isFalse();
    assertThat(entry.belongsTo(ACCOUNT_REF, "00000009999")).isFalse();
    assertThat(entry.belongsTo(null, POLICY_NO)).isFalse();
  }

  @Test
  void rejectsBlankScalarFields() {
    var codes = List.of("FAEU");
    assertThatThrownBy(() -> new FundComparisonOptionsCacheEntry(" ", POLICY_NO, "10D", codes))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accountRef");
    assertThatThrownBy(() -> new FundComparisonOptionsCacheEntry(ACCOUNT_REF, null, "10D", codes))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("policyNo");
    assertThatThrownBy(() -> new FundComparisonOptionsCacheEntry(ACCOUNT_REF, POLICY_NO, "", codes))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rawTimeRange");
  }

  @Test
  void rejectsMissingEmptyOrBlankFundCodes() {
    var withNull = new ArrayList<String>();
    withNull.add(null);

    assertThatThrownBy(() -> entry(null)).isInstanceOf(NullPointerException.class);
    var emptyFundCodes = List.<String>of();
    assertThatThrownBy(() -> entry(emptyFundCodes)).isInstanceOf(IllegalArgumentException.class);
    var blankFundCode = List.of("FAEU", " ");
    assertThatThrownBy(() -> entry(blankFundCode)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> entry(withNull)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fundCodesAreAnImmutableCopy() {
    var source = new ArrayList<>(List.of("FAEU"));
    var entry = entry(source);
    source.add("FBAU");

    assertThat(entry.fundCodes()).containsExactly("FAEU");
    var codes = entry.fundCodes();
    assertThatThrownBy(() -> codes.add("X")).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void toStringHidesAccountRefAndPolicyNo() {
    var text = entry(List.of("FAEU")).toString();

    assertThat(text).doesNotContain(ACCOUNT_REF, POLICY_NO).contains("FAEU", "10D,1M");
  }
}
