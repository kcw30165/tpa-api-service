package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import java.util.List;
import java.util.Objects;

public sealed interface InvestmentChoiceSubmitOutcome
    permits InvestmentChoiceSubmitOutcome.Submitted, InvestmentChoiceSubmitOutcome.Invalid {

  record Submitted(InvestmentChoiceSubmission submission) implements InvestmentChoiceSubmitOutcome {
    public Submitted {
      Objects.requireNonNull(submission, "submission must not be null");
    }
  }

  record Invalid(List<InvestmentChoiceViolation> violations)
      implements InvestmentChoiceSubmitOutcome {
    public Invalid {
      violations = List.copyOf(Objects.requireNonNull(violations, "violations must not be null"));
      if (violations.isEmpty()) {
        throw new IllegalArgumentException("violations must not be empty");
      }
    }
  }
}
