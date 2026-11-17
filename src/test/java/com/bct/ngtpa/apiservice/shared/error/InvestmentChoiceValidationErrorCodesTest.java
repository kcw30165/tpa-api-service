package com.bct.ngtpa.apiservice.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class InvestmentChoiceValidationErrorCodesTest {

  @Test
  void exposesTheInvestmentChoiceErrorCodesFromTheContract() {
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
                ErrorCodes.INVESTMENT_CHOICE_FUND_UNAVAILABLE,
                ErrorCodes.INVESTMENT_CHOICE_CANCEL_UNAVAILABLE,
                ErrorCodes.INVESTMENT_CHOICE_CANCEL_NOT_CANCELLABLE,
                ErrorCodes.INVESTMENT_CHOICE_CANCEL_REF_REQUIRED,
                ErrorCodes.INVESTMENT_CHOICE_CANCEL_REF_INVALID,
                ErrorCodes.INVESTMENT_CHOICE_FUNCTION_UNAVAILABLE,
                ErrorCodes.INVESTMENT_CHOICE_MEMBER_INELIGIBLE,
                ErrorCodes.INVESTMENT_CHOICE_SUBMISSION_SAME_SOURCE_PENDING))
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
            "err.investment-choice.fund.unavailable",
            "err.investment-choice.cancel.unavailable",
            "err.investment-choice.cancel.not-cancellable",
            "err.investment-choice.cancel.ref-required",
            "err.investment-choice.cancel.ref-invalid",
            "err.investment-choice.function-unavailable",
            "err.investment-choice.member-ineligible",
            "err.investment-choice.submission.same-source-pending");
  }
}
