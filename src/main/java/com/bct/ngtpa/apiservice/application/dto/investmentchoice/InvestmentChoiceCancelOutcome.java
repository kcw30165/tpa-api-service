package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import java.util.List;
import java.util.Objects;

public sealed interface InvestmentChoiceCancelOutcome
    permits InvestmentChoiceCancelOutcome.Cancelled, InvestmentChoiceCancelOutcome.Invalid {

  record Cancelled(InvestmentChoiceCancellation cancellation)
      implements InvestmentChoiceCancelOutcome {
    public Cancelled {
      Objects.requireNonNull(cancellation, "cancellation must not be null");
    }
  }

  record Invalid(List<InvestmentChoiceViolation> violations)
      implements InvestmentChoiceCancelOutcome {
    public Invalid {
      violations = List.copyOf(Objects.requireNonNull(violations, "violations must not be null"));
      if (violations.isEmpty()) {
        throw new IllegalArgumentException("violations must not be empty");
      }
    }
  }
}
