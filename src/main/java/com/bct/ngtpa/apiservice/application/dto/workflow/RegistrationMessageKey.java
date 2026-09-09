package com.bct.ngtpa.apiservice.application.dto.workflow;

public enum RegistrationMessageKey {
  COMPLETION("registration.completion");

  private final String value;

  RegistrationMessageKey(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
