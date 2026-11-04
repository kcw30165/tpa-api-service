package com.bct.ngtpa.apiservice.application.dto.accountsummary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.CurrencySelection;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountSummaryCurrencyCacheEntryTest {

  @Test
  void rejectsBlankAccountRef() {
    var currencies = List.of("HKD");
    assertThatThrownBy(() -> new AccountSummaryCurrencyCacheEntry(" ", "00000008802", currencies))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBlankPolicyNo() {
    var currencies = List.of("HKD");
    assertThatThrownBy(() -> new AccountSummaryCurrencyCacheEntry("ACC-1", "", currencies))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNullAvailableCurrencies() {
    assertThatThrownBy(() -> new AccountSummaryCurrencyCacheEntry("ACC-1", "00000008802", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsEmptyAvailableCurrencies() {
    var currencies = List.<String>of();
    assertThatThrownBy(
            () -> new AccountSummaryCurrencyCacheEntry("ACC-1", "00000008802", currencies))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsABlankCurrencyEntry() {
    var currencies = java.util.Arrays.asList("HKD", " ");
    assertThatThrownBy(
            () -> new AccountSummaryCurrencyCacheEntry("ACC-1", "00000008802", currencies))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void ofBuildsFromACurrencySelection() {
    var entry =
        AccountSummaryCurrencyCacheEntry.of(
            "ACC-1", "00000008802", new CurrencySelection("HKD", List.of("HKD", "USD")));
    assertThat(entry.accountRef()).isEqualTo("ACC-1");
    assertThat(entry.policyNo()).isEqualTo("00000008802");
    assertThat(entry.availableCurrencies()).containsExactly("HKD", "USD");
  }

  @Test
  void belongsToIsTrueOnlyWhenBothIdentifiersMatch() {
    var entry = new AccountSummaryCurrencyCacheEntry("ACC-1", "00000008802", List.of("HKD"));
    assertThat(entry.belongsTo("ACC-1", "00000008802")).isTrue();
    assertThat(entry.belongsTo("ACC-2", "00000008802")).isFalse();
    assertThat(entry.belongsTo("ACC-1", "00000009999")).isFalse();
  }

  @Test
  void toStringHidesAccountRefAndPolicyNo() {
    var entry = new AccountSummaryCurrencyCacheEntry("ACC-SECRET", "POLICY-SECRET", List.of("HKD"));
    assertThat(entry.toString()).doesNotContain("ACC-SECRET").doesNotContain("POLICY-SECRET");
    assertThat(entry.toString()).contains("HKD");
  }
}
