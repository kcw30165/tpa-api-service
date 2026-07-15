package com.bct.ngtpa.apiservice.application.dto.workflow;

/** Canonical public errors shared by registration and other workflows. */
public enum SharedWorkflowErrorKey {
    IDEMPOTENCY_MISSING("err.workflow.idempotency.missing"),
    IDEMPOTENCY_IN_PROGRESS("err.workflow.idempotency.inProgress"),
    IDEMPOTENCY_KEY_REUSED("err.workflow.idempotency.keyReused"),
    DOWNSTREAM_BUSINESS_REJECTED("err.workflow.downstream.businessRejected"),
    DOWNSTREAM_UNAVAILABLE("err.workflow.downstream.unavailable"),
    SYSTEM_UNAVAILABLE("err.workflow.system.unavailable");
    private final String value;
    SharedWorkflowErrorKey(String value) { this.value = value; }
    public String value() { return value; }
}
