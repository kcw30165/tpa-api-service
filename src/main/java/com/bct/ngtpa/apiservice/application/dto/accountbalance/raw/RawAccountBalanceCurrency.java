package com.bct.ngtpa.apiservice.application.dto.accountbalance.raw;

import java.util.List;

public record RawAccountBalanceCurrency(List<String> available, String selected) {
  public RawAccountBalanceCurrency {
    available = available == null ? List.of() : List.copyOf(available);
  }
}
