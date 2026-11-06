package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import java.util.List;

public record InvestmentChoiceDetails(
    int intervalPcnt,
    List<InvestmentChoiceSource> sources,
    List<InvestmentChoiceFund> funds,
    List<InvestmentChoiceMandate> mandates,
    List<InvestmentChoicePending> pending) {

  public InvestmentChoiceDetails {
    sources = List.copyOf(sources);
    funds = List.copyOf(funds);
    mandates = List.copyOf(mandates);
    pending = List.copyOf(pending);
  }
}
