package com.bct.ngtpa.apiservice.application.service.accountbalance;

import static org.assertj.core.api.Assertions.*;

import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.RawAccountBalanceCurrency;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountBalanceCurrencySelectionMapperTest {
  private final AccountBalanceCurrencySelectionMapper mapper =
      new AccountBalanceCurrencySelectionMapper();

  @Test
  void mapsAndPreservesAvailableOrder() {
    var result = mapper.map(new RawAccountBalanceCurrency(List.of("HKD", "USD"), "USD"));
    assertThat(result.selectedCurrency()).isEqualTo("USD");
    assertThat(result.availableCurrencies()).containsExactly("HKD", "USD");
  }

  @Test
  void rejectsInvalidCurrencyStructures() {
    assertFailure(new RawAccountBalanceCurrency(List.of("HKD", "HKD"), "HKD"));
    assertFailure(new RawAccountBalanceCurrency(java.util.Arrays.asList("HKD", null), "HKD"));
    assertFailure(new RawAccountBalanceCurrency(List.of("HKD", " "), "HKD"));
    assertFailure(new RawAccountBalanceCurrency(List.of("HKD"), null));
    assertFailure(new RawAccountBalanceCurrency(List.of("HKD"), " "));
    assertFailure(new RawAccountBalanceCurrency(List.of("HKD"), "USD"));
  }

  private void assertFailure(RawAccountBalanceCurrency value) {
    assertThatThrownBy(() -> mapper.map(value))
        .isInstanceOf(AccountBalanceRetrievalException.class);
  }
}
