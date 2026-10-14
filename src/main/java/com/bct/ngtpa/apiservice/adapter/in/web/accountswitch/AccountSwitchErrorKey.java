package com.bct.ngtpa.apiservice.adapter.in.web.accountswitch;

/** Error codes specific to the account-switch endpoint, following each flow's own-enum convention. */
public enum AccountSwitchErrorKey {
  CSRF_INVALID("err.account.switch.csrf.invalid");

  private final String value;

  AccountSwitchErrorKey(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
