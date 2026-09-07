package com.bct.ngtpa.apiservice.application.dto.accountgainloss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.exception.AccountGainLossRetrievalException;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class AccountGainLossFoundationContractTest {
  @Test
  void periodIsBusinessOnly() {
    assertThat(AccountGainLossPeriod.values())
        .containsExactly(
            AccountGainLossPeriod.SINCE_SCHEME_INCEPTION, AccountGainLossPeriod.YEAR_TO_DATE);
    assertThat(AccountGainLossPeriod.class.getDeclaredMethods())
        .noneMatch(method -> method.getName().equals("toApimValue"));
  }

  @Test
  void rawAndPublicFinancialTypesAreSeparated() {
    assertThat(componentType(RawFundGainLoss.class, "gainLoss")).isEqualTo(BigDecimal.class);
    assertThat(componentType(AccountGainLossFund.class, "gainLoss")).isEqualTo(String.class);
    assertThat(componentType(AccountGainLossResult.class, "totalGainLoss")).isEqualTo(String.class);
  }

  @Test
  void resultAndRawDataDefensivelyCopyFunds() {
    var rawSource = new ArrayList<RawFundGainLoss>();
    rawSource.add(new RawFundGainLoss("A", "A", "A", 1, BigDecimal.ONE));
    var raw = new RawAccountGainLossData(LocalDate.of(2026, 9, 1), rawSource);
    rawSource.clear();
    assertThat(raw.funds()).hasSize(1);
    assertThatThrownBy(() -> raw.funds().add(null))
        .isInstanceOf(UnsupportedOperationException.class);

    var publicSource = new ArrayList<AccountGainLossFund>();
    publicSource.add(new AccountGainLossFund("A", "A", 1, "1.00"));
    var result =
        new AccountGainLossResult(
            AccountGainLossPeriod.SINCE_SCHEME_INCEPTION, "01/09/2026", "1.00", publicSource);
    publicSource.clear();
    assertThat(result.funds()).hasSize(1);
    assertThatThrownBy(() -> result.funds().add(null))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void typedFailureReasonsRemainStable() {
    assertThat(AccountGainLossRetrievalException.fundSwitchingInProgress().reason())
        .isEqualTo(AccountGainLossRetrievalException.Reason.FUND_SWITCHING_IN_PROGRESS);
    assertThat(AccountGainLossRetrievalException.systemFailure().reason())
        .isEqualTo(AccountGainLossRetrievalException.Reason.SYSTEM_FAILURE);
  }

  private static Class<?> componentType(Class<?> type, String name) {
    return java.util.Arrays.stream(type.getRecordComponents())
        .filter(component -> component.getName().equals(name))
        .map(RecordComponent::getType)
        .findFirst()
        .orElseThrow();
  }
}
