package com.bct.ngtpa.apiservice.adapter.in.web.account;

/** Error codes specific to the account endpoints, following each flow's own-enum convention. */
public enum AccountErrorKey {
  CSRF_INVALID("err.account.csrf.invalid"),
  ACCOUNT_NOT_FOUND("err.account.not.found");

  private final String value;

  AccountErrorKey(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
