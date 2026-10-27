package com.bct.ngtpa.apiservice.adapter.in.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record PersonalInformationUpdateResultResponse(Data data) {
  public record Data(
      @JsonInclude(JsonInclude.Include.ALWAYS) Account selectedAccount,
      List<Account> rows,
      List<AccountErrors> errors) {
    public Data {
      rows = List.copyOf(rows);
      errors = List.copyOf(errors);
    }
  }

  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Account(
      String accountIdentifier, String refNo, String submitDate, String submitTime) {}

  public record AccountErrors(String accountIdentifier, List<MappedError> errors) {
    public AccountErrors {
      errors = List.copyOf(errors);
    }
  }
}
