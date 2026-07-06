package com.bct.ngtpa.apiservice.application.dto.workflow;

public final class RegistrationWorkflowMessageKeys {
    public static final WorkflowMessageKey REGISTRATION_SUCCESS = new WorkflowMessageKey("registration.success");
    public static final WorkflowMessageKey REGISTRATION_OTP_SENT = new WorkflowMessageKey("registration.otp.sent");
    public static final WorkflowMessageKey REGISTRATION_OTP_RESENT = new WorkflowMessageKey("registration.otp.resent");
    public static final WorkflowMessageKey REGISTRATION_USERNAME_AVAILABLE = new WorkflowMessageKey("registration.username.available");
    public static final WorkflowMessageKey REGISTRATION_CANCELLED = new WorkflowMessageKey("registration.cancelled");

    private RegistrationWorkflowMessageKeys() {
    }
}
