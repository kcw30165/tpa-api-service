package com.bct.ngtpa.apiservice.application.dto.workflow;

/**
 * Central registration workflow page references.
 *
 * <p>These identifiers are workflow vocabulary used by response payloads and
 * correspond to configured page definitions. Keep page ids centralized here so
 * web mappers do not contain scattered page-id literals.</p>
 */
public final class RegistrationWorkflowPageRefs {

    private RegistrationWorkflowPageRefs() {
    }

    public static WorkflowPageRef otpVerification() {
        return new WorkflowPageRef(RegistrationWorkflowConstants.PAGE_REGISTRATION_OTP_VERIFICATION, null);
    }

    public static WorkflowPageRef forNextStep(WorkflowNextStep nextStep) {
        if (nextStep == WorkflowNextStep.VERIFY_OTP) {
            return otpVerification();
        }
        return null;
    }
}
