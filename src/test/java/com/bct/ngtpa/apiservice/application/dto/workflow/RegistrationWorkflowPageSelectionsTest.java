package com.bct.ngtpa.apiservice.application.dto.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RegistrationWorkflowPageSelectionsTest {

    @Test
    void registrationWorkflowPageSelectionsMatchExpectedPageIds() {
        assertThat(RegistrationWorkflowPageSelections.registrationStart().pageRef().pageId()).isEqualTo("registration-start");
        assertThat(RegistrationWorkflowPageSelections.registrationOtpVerification().pageRef().pageId()).isEqualTo("registration-otp-verification");
        assertThat(RegistrationWorkflowPageSelections.registrationTrustSelection().pageRef().pageId()).isEqualTo("registration-trust-selection");
        assertThat(RegistrationWorkflowPageSelections.registrationUsernameCheck().pageRef().pageId()).isEqualTo("registration-username-check");
        assertThat(RegistrationWorkflowPageSelections.registrationPasswordSetup().pageRef().pageId()).isEqualTo("registration-password-setup");
        assertThat(RegistrationWorkflowPageSelections.registrationSuccessNoRemainingTrusts().pageRef().pageId()).isEqualTo("registration-success");
        assertThat(RegistrationWorkflowPageSelections.registrationSuccessHasRemainingTrusts().pageRef().pageId()).isEqualTo("registration-success");
    }

    @Test
    void registrationWorkflowPageSelectionsMatchExpectedSuccessVariants() {
        assertThat(RegistrationWorkflowPageSelections.registrationSuccessNoRemainingTrusts().pageRef().variant()).isEqualTo("noRemainingTrusts");
        assertThat(RegistrationWorkflowPageSelections.registrationSuccessHasRemainingTrusts().pageRef().variant()).isEqualTo("hasRemainingTrusts");
    }

    @Test
    void registrationWorkflowPageSelectionsUseExpectedScenarioAndNextSteps() {
        assertThat(RegistrationWorkflowPageSelections.registrationStart().scenario()).isEqualTo(WorkflowScenario.REGISTRATION);
        assertThat(RegistrationWorkflowPageSelections.registrationStart().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
        assertThat(RegistrationWorkflowPageSelections.registrationOtpVerification().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
        assertThat(RegistrationWorkflowPageSelections.registrationTrustSelection().nextStep()).isEqualTo(WorkflowNextStep.SELECT_TRUST);
        assertThat(RegistrationWorkflowPageSelections.registrationUsernameCheck().nextStep()).isEqualTo(WorkflowNextStep.USERNAME_CHECK);
        assertThat(RegistrationWorkflowPageSelections.registrationPasswordSetup().nextStep()).isEqualTo(WorkflowNextStep.PASSWORD_SETUP);
        assertThat(RegistrationWorkflowPageSelections.registrationSuccessNoRemainingTrusts().nextStep()).isEqualTo(WorkflowNextStep.REGISTRATION_SUCCESS);
        assertThat(RegistrationWorkflowPageSelections.registrationSuccessHasRemainingTrusts().nextStep()).isEqualTo(WorkflowNextStep.REGISTRATION_SUCCESS);
    }
}
