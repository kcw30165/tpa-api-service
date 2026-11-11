package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import java.util.Objects;

public record InvestmentChoiceInstructionRow(String switchSource, String fundCode, int percentage) {

  public InvestmentChoiceInstructionRow {
    Objects.requireNonNull(switchSource, "switchSource must not be null");
    Objects.requireNonNull(fundCode, "fundCode must not be null");
  }
}
