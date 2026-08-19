package com.bct.ngtpa.apiservice.application.dto.workflow;

public enum WorkflowScenario {
  REGISTRATION(true),
  LOGIN(true),
  FORGOT_PASSWORD(true),
  USERNAME_RECOVERY(true),
  FUND_SWITCHING(false);

  private final boolean preAuth;

  WorkflowScenario(boolean preAuth) {
    this.preAuth = preAuth;
  }

  public boolean isPreAuth() {
    return preAuth;
  }

  public boolean isPostLogin() {
    return !preAuth;
  }
}
