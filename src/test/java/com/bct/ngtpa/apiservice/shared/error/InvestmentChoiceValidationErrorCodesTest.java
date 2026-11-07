package com.bct.ngtpa.apiservice.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class InvestmentChoiceValidationErrorCodesTest {

  @Test
  void exposesTheValidationCodesFromTheCimContract() {
    assertThat(
            List.of(
                ErrorCodes.INVESTMENT_CHOICE_REQUEST_INVALID,
                ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_REQUIRED,
                ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_DUPLICATE,
                ErrorCodes.INVESTMENT_CHOICE_ALLOCATION_REQUIRED,
                ErrorCodes.INVESTMENT_CHOICE_FUND_REQUIRED,
                ErrorCodes.INVESTMENT_CHOICE_FUND_DUPLICATE,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_INVALID,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_TOTAL_INVALID,
                ErrorCodes.INVESTMENT_CHOICE_PERCENTAGE_INTERVAL_INVALID,
                ErrorCodes.INVESTMENT_CHOICE_SWITCH_SOURCE_UNAVAILABLE,
                ErrorCodes.INVESTMENT_CHOICE_FUND_UNAVAILABLE))
        .containsExactly(
            "err.investment-choice.request.invalid",
            "err.investment-choice.switch-source.required",
            "err.investment-choice.switch-source.duplicate",
            "err.investment-choice.allocation.required",
            "err.investment-choice.fund.required",
            "err.investment-choice.fund.duplicate",
            "err.investment-choice.percentage.invalid",
            "err.investment-choice.percentage.total-invalid",
            "err.investment-choice.percentage.interval-invalid",
            "err.investment-choice.switch-source.unavailable",
            "err.investment-choice.fund.unavailable");
  }
}
