package com.bct.ngtpa.apiservice.application.dto.workflow;

public final class RegistrationWorkflowPageSelections {

    private RegistrationWorkflowPageSelections() {
    }

    public static WorkflowPageSelection registrationStart() {
        return selection(WorkflowNextStep.VERIFY_OTP, RegistrationWorkflowConstants.PAGE_REGISTRATION_START, null);
    }

    public static WorkflowPageSelection registrationOtpVerification() {
        return selection(WorkflowNextStep.VERIFY_OTP, RegistrationWorkflowConstants.PAGE_REGISTRATION_OTP_VERIFICATION, null);
    }

    public static WorkflowPageSelection registrationTrustSelection() {
        return selection(WorkflowNextStep.SELECT_TRUST, RegistrationWorkflowConstants.PAGE_REGISTRATION_TRUST_SELECTION, null);
    }

    public static WorkflowPageSelection registrationUsernameCheck() {
        return selection(WorkflowNextStep.USERNAME_CHECK, RegistrationWorkflowConstants.PAGE_REGISTRATION_USERNAME_CHECK, null);
    }

    public static WorkflowPageSelection registrationPasswordSetup() {
        return selection(WorkflowNextStep.PASSWORD_SETUP, RegistrationWorkflowConstants.PAGE_REGISTRATION_PASSWORD_SETUP, null);
    }

    public static WorkflowPageSelection registrationSuccessNoRemainingTrusts() {
        return selection(
                WorkflowNextStep.REGISTRATION_SUCCESS,
                RegistrationWorkflowConstants.PAGE_REGISTRATION_SUCCESS,
                RegistrationWorkflowConstants.SUCCESS_VARIANT_NO_REMAINING_TRUSTS);
    }

    public static WorkflowPageSelection registrationSuccessHasRemainingTrusts() {
        return selection(
                WorkflowNextStep.REGISTRATION_SUCCESS,
                RegistrationWorkflowConstants.PAGE_REGISTRATION_SUCCESS,
                RegistrationWorkflowConstants.SUCCESS_VARIANT_HAS_REMAINING_TRUSTS);
    }

    private static WorkflowPageSelection selection(WorkflowNextStep nextStep, String pageId, String variant) {
        return new WorkflowPageSelection(
                WorkflowScenario.REGISTRATION,
                nextStep,
                new WorkflowPageRef(pageId, variant));
    }
}
