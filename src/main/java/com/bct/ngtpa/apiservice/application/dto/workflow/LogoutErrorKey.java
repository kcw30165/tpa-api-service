package com.bct.ngtpa.apiservice.application.dto.workflow;

/** Canonical public errors owned by authenticated logout. */
public enum LogoutErrorKey implements WorkflowErrorKey {
    CSRF_INVALID("err.logout.csrf.invalid");

    private final String value;

    LogoutErrorKey(String value) {
        this.value = WorkflowKeyValidator.validate(value, "Invalid logout error key");
    }

    @Override
    public String value() {
        return value;
    }
}
