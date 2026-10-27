package com.bct.ngtpa.apiservice.adapter.in.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record MappedError(
    @JsonInclude(JsonInclude.Include.ALWAYS) String type, String code, List<String> targets) {
  public MappedError {
    targets = List.copyOf(targets);
  }
}
