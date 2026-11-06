package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

public record InvestmentChoiceSubmission(
    String referenceNo, LocalDate submitDate, LocalTime submitTime) {

  public InvestmentChoiceSubmission {
    Objects.requireNonNull(referenceNo, "referenceNo must not be null");
    Objects.requireNonNull(submitDate, "submitDate must not be null");
    Objects.requireNonNull(submitTime, "submitTime must not be null");
  }
}
