package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import java.util.List;
import java.util.Objects;

public record InvestmentChoiceInstructionRequest(
    List<String> switchSources, List<Allocation> allocations) {

  public InvestmentChoiceInstructionRequest {
    switchSources =
        List.copyOf(Objects.requireNonNull(switchSources, "switchSources must not be null"));
    allocations = List.copyOf(Objects.requireNonNull(allocations, "allocations must not be null"));
  }

  public record Allocation(String fundCode, int percentage) {}
}
