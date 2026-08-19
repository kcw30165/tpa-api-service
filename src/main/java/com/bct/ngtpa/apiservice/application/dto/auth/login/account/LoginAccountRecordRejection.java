package com.bct.ngtpa.apiservice.application.dto.auth.login.account;

public record LoginAccountRecordRejection(
    int recordIndex, LoginAccountRecordRejectionCategory category) {
  public LoginAccountRecordRejection {
    if (recordIndex < 0) throw new IllegalArgumentException("recordIndex must not be negative");
    if (category == null) throw new IllegalArgumentException("category required");
  }
}
