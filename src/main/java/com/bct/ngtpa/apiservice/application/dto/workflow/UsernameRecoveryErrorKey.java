package com.bct.ngtpa.apiservice.application.dto.workflow;

/** Canonical public errors owned by the username recovery workflow. */
public enum UsernameRecoveryErrorKey implements WorkflowErrorKey {
  AUTH_FLOW_INVALID("err.usernamerecovery.authFlow.invalid"),
  CSRF_INVALID("err.usernamerecovery.csrf.invalid"),
  REGISTRATION_REQUIRED("err.usernamerecovery.registration.required"),
  REQUEST_ALREADY_REQUESTED("err.usernamerecovery.request.alreadyRequested"),
  SCHEME_REF_INVALID("err.usernamerecovery.schemeRef.invalid"),
  VERIFICATION_FAILED("err.usernamerecovery.verification.failed"),
  WORKFLOW_COMPLETED("err.usernamerecovery.workflow.completed");

  private final String value;

  UsernameRecoveryErrorKey(String value) {
    this.value = WorkflowKeyValidator.validate(value, "Invalid username recovery error key");
  }

  @Override
  public String value() {
    return value;
  }
}
