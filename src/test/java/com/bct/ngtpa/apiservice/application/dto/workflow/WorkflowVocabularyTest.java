package com.bct.ngtpa.apiservice.application.dto.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class WorkflowVocabularyTest {

        @Test
        void workflowScenarioDefinesExpectedValues() {
                assertThat(WorkflowScenario.values()).containsExactly(
                                WorkflowScenario.REGISTRATION,
                                WorkflowScenario.LOGIN,
                                WorkflowScenario.FORGOT_PASSWORD,
                                WorkflowScenario.USERNAME_RECOVERY,
                                WorkflowScenario.FUND_SWITCHING);
                assertThat(Arrays.stream(WorkflowScenario.values()).map(Enum::name))
                                .doesNotContain("CHANGE_PASSWORD");
        }

        @Test
        void workflowScenarioClassifiesPreAuthAndPostLogin() {
                assertThat(WorkflowScenario.REGISTRATION.isPreAuth()).isTrue();
                assertThat(WorkflowScenario.LOGIN.isPreAuth()).isTrue();
                assertThat(WorkflowScenario.FORGOT_PASSWORD.isPreAuth()).isTrue();
                assertThat(WorkflowScenario.USERNAME_RECOVERY.isPreAuth()).isTrue();
                assertThat(WorkflowScenario.FUND_SWITCHING.isPostLogin()).isTrue();
                assertThat(WorkflowScenario.FUND_SWITCHING.isPreAuth()).isFalse();
        }

        @Test
        void workflowStepDefinesExpectedCommonValues() {
                assertThat(WorkflowStep.values()).containsExactly(
                                WorkflowStep.START,
                                WorkflowStep.VERIFY_OTP,
                                WorkflowStep.SELECT_TRUST,
                                WorkflowStep.USERNAME_CHECK,
                                WorkflowStep.PASSWORD_SETUP,
                                WorkflowStep.COMPLETE,
                                WorkflowStep.SUCCESS,
                                WorkflowStep.CANCEL,
                                WorkflowStep.WAIT,
                                WorkflowStep.UNKNOWN);
        }

        @Test
        void workflowNextStepDefinesSharedRegistrationAndLoginValues() {
                assertThat(WorkflowNextStep.values()).containsExactly(
                                WorkflowNextStep.SELECT_OTP_DESTINATION,
                                WorkflowNextStep.VERIFY_OTP,
                                WorkflowNextStep.ENTER_USERNAME,
                                WorkflowNextStep.ENTER_ID,
                                WorkflowNextStep.USERNAME_CHECK,
                                WorkflowNextStep.ENTER_DETAILS,
                                WorkflowNextStep.SELECT_TRUST,
                                WorkflowNextStep.SELECT_ACCOUNT,
                                WorkflowNextStep.PASSWORD_SETUP,
                                WorkflowNextStep.REGISTRATION_SUCCESS,
                                WorkflowNextStep.PASSWORD_RESET_SUCCESS,
                                WorkflowNextStep.RETURN_TO_LOGIN,
                                WorkflowNextStep.WAIT,
                                WorkflowNextStep.RESTART_FLOW,
                                WorkflowNextStep.RESEND_OTP,
                                WorkflowNextStep.NONE);
        }

        @Test
        void workflowNextStepDoesNotContainRemovedTrustStates() {
                assertThat(Arrays.stream(WorkflowNextStep.values()).map(Enum::name))
                                .doesNotContain("TRUST_SELECTED", "SELECT_REMAINING_TRUST");
        }

        @Test
        void registrationWorkflowPageConstantsMatchBffPagesIds() {
                assertThat(RegistrationWorkflowConstants.PAGE_REGISTRATION_START).isEqualTo("registration-start");
                assertThat(RegistrationWorkflowConstants.PAGE_REGISTRATION_OTP_VERIFICATION)
                                .isEqualTo("registration-otp-verification");
                assertThat(RegistrationWorkflowConstants.PAGE_REGISTRATION_TRUST_SELECTION)
                                .isEqualTo("registration-trust-selection");
                assertThat(RegistrationWorkflowConstants.PAGE_REGISTRATION_USERNAME_CHECK)
                                .isEqualTo("registration-username-check");
                assertThat(RegistrationWorkflowConstants.PAGE_REGISTRATION_PASSWORD_SETUP)
                                .isEqualTo("registration-password-setup");
                assertThat(RegistrationWorkflowConstants.PAGE_REGISTRATION_SUCCESS).isEqualTo("registration-success");
                assertThat(RegistrationWorkflowConstants.SUCCESS_VARIANT_NO_REMAINING_TRUSTS)
                                .isEqualTo("noRemainingTrusts");
                assertThat(RegistrationWorkflowConstants.SUCCESS_VARIANT_HAS_REMAINING_TRUSTS)
                                .isEqualTo("hasRemainingTrusts");
        }

        private static java.util.List<String> recordComponentNames(Class<?> type) {
                return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
        }
}
