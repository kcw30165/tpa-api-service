package com.bct.ngtpa.apiservice.application.dto.fundprice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FundPriceRiskLevelsTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "0", " 0 "})
  void unassignedValuesAreNotAssignedAndHaveNoSortKey(String value) {
    assertThat(FundPriceRiskLevels.isAssigned(value)).isFalse();
    assertThat(FundPriceRiskLevels.sortKey(value)).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"0.5", "1", "4.5", "5"})
  void realRiskLevelsAreAssignedWithNumericSortKey(String value) {
    assertThat(FundPriceRiskLevels.isAssigned(value)).isTrue();
    assertThat(FundPriceRiskLevels.sortKey(value)).isEqualByComparingTo(new BigDecimal(value));
  }

  @Test
  void unparseableAssignedValueHasNoSortKey() {
    assertThat(FundPriceRiskLevels.isAssigned("high")).isTrue();
    assertThat(FundPriceRiskLevels.sortKey("high")).isNull();
  }
}
