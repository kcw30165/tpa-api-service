package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import java.util.List;
import java.util.Objects;

public record InvestmentChoiceViolation(Kind kind, String code, List<String> targets) {

  public InvestmentChoiceViolation {
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(code, "code must not be null");
    targets = List.copyOf(Objects.requireNonNull(targets, "targets must not be null"));
  }

  public enum Kind {
    FIELD,
    CROSS_FIELD,
    FORM
  }
}
