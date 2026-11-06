package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record InvestmentChoicePending(
    String refNo,
    LocalDate submitDate,
    LocalTime submitTime,
    List<InvestmentChoicePendingDetail> details) {

  public InvestmentChoicePending {
    details = List.copyOf(details);
  }
}
