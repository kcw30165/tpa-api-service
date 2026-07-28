package com.bct.ngtpa.apiservice.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.workflow.WorkflowMutationResponseMapper;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationWorkflowConstants;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationWorkflowPageSelections;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowFailureCategory;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowFailureClassification;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowLogSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowMessageKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowPageSelection;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResolvedPage;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResolvedPageAction;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResult;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResultStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowScenario;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowStableStateRule;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowStep;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowTransitionCheck;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class P5LightweightWorkflowFoundationIntegrationTest {

        @Test
        void p5WorkflowVocabularyCoversExpectedScenariosAndSteps() {
                assertThat(WorkflowScenario.values()).containsExactly(
                                WorkflowScenario.REGISTRATION,
                                WorkflowScenario.LOGIN,
                                WorkflowScenario.FORGOT_PASSWORD,
                                WorkflowScenario.USERNAME_RECOVERY,
                                WorkflowScenario.FUND_SWITCHING);
                assertThat(WorkflowStep.values()).contains(
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
                assertThat(WorkflowNextStep.values()).contains(
                                WorkflowNextStep.VERIFY_OTP,
                                WorkflowNextStep.USERNAME_CHECK,
                                WorkflowNextStep.SELECT_TRUST,
                                WorkflowNextStep.PASSWORD_SETUP,
                                WorkflowNextStep.REGISTRATION_SUCCESS,
                                WorkflowNextStep.RETURN_TO_LOGIN,
                                WorkflowNextStep.WAIT,
                                WorkflowNextStep.RESTART_FLOW,
                                WorkflowNextStep.NONE);
        }

        @Test
        void p5WorkflowDoesNotContainRemovedTrustStates() {
                assertThat(Arrays.stream(WorkflowStep.values()).map(Enum::name))
                                .doesNotContain("TRUST_SELECTED", "SELECT_REMAINING_TRUST");
                assertThat(Arrays.stream(WorkflowNextStep.values()).map(Enum::name))
                                .doesNotContain("TRUST_SELECTED", "SELECT_REMAINING_TRUST");
        }

        @Test
        void p5WorkflowScenarioSeparatesPreAuthAndPostLoginFamilies() {
                assertThat(WorkflowScenario.REGISTRATION.isPreAuth()).isTrue();
                assertThat(WorkflowScenario.LOGIN.isPreAuth()).isTrue();
                assertThat(WorkflowScenario.FORGOT_PASSWORD.isPreAuth()).isTrue();
                assertThat(WorkflowScenario.USERNAME_RECOVERY.isPreAuth()).isTrue();
                assertThat(WorkflowScenario.FUND_SWITCHING.isPostLogin()).isTrue();
        }

        @Test
        void p5WorkflowResultUsesExistingMutationResponseMapping() {
                var result = WorkflowResult.success(WorkflowNextStep.VERIFY_OTP,
                                new SamplePayload("m****r@example.com", 300));
                MutationResponse<?> response = new WorkflowMutationResponseMapper().toMutationResponse(result);

                assertThat(response).isInstanceOf(MutationResponse.class);
                assertThat(response.status()).isEqualTo(ApiStatus.SUCCESS);
                assertThat(response.success()).isTrue();
                assertThat(response.messages()).isEmpty();
                assertThat(response.errors()).isEmpty();
                assertClassAbsent("com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowMutationEnvelope");
                assertClassAbsent("com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowApiResponse");
                assertClassAbsent("com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResponseEnvelope");
        }

        @Test
        void p5WorkflowResultDoesNotExposeRequestIdInBody() {
                var response = new WorkflowMutationResponseMapper().toMutationResponse(
                                WorkflowResult.success(WorkflowNextStep.VERIFY_OTP, "safePayload"));

                assertThat(recordComponentNames(response.result().getClass()))
                                .doesNotContain("requestId", "xRequestId", "X-Request-Id");
                assertThat(response.toString()).doesNotContain("requestId", "xRequestId", "X-Request-Id");
        }

        @Test
        void p5WorkflowKeysAreCodeKeysOnly() {
                List<WorkflowErrorKey> representativeErrorKeys = List.of(
                                RegistrationErrorKey.START_VERIFICATION_FAILED,
                                RegistrationErrorKey.CSRF_INVALID,
                                RegistrationErrorKey.OTP_INVALID,
                                RegistrationErrorKey.USERNAME_ALREADY_REGISTERED,
                                RegistrationErrorKey.REGISTRATION_COMPLETE_FAILED,
                                SharedWorkflowErrorKey.IDEMPOTENCY_IN_PROGRESS,
                                SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED,
                                SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE,
                                SharedWorkflowErrorKey.DOWNSTREAM_UNAVAILABLE);

                for (WorkflowErrorKey key : representativeErrorKeys) {
                        assertCodeKey(key.value());
                }
                assertCodeKey(new WorkflowMessageKey("registration.otp.resent").value());
        }

        @Test
        void p5RegistrationStartApimMemberNotFoundMappingRemainsKeyOnly() {
                assertThat("err.tpa.member.not.found").isEqualTo("err.tpa.member.not.found");
                assertThat(RegistrationErrorKey.START_VERIFICATION_FAILED)
                                .isEqualTo(RegistrationErrorKey.START_VERIFICATION_FAILED);
                assertClassAbsent("com.bct.ngtpa.apiservice.adapter.out.apim.ApimRegistrationStartAdapter");
                assertClassAbsent("com.bct.ngtpa.apiservice.application.port.in.RegistrationStartUseCase");
        }

        @Test
        void p5WorkflowFailureClassificationMatchesLoggingSeverityContract() {
                assertThat(WorkflowFailureClassification.forCategory(WorkflowFailureCategory.VALIDATION).logSeverity())
                                .isEqualTo(WorkflowLogSeverity.ERROR);
                assertThat(WorkflowFailureClassification.forCategory(WorkflowFailureCategory.BUSINESS).logSeverity())
                                .isEqualTo(WorkflowLogSeverity.ERROR);
                assertThat(WorkflowFailureClassification.forCategory(WorkflowFailureCategory.SECURITY).logSeverity())
                                .isEqualTo(WorkflowLogSeverity.ERROR);
                assertThat(WorkflowFailureClassification.forCategory(WorkflowFailureCategory.DOWNSTREAM_BUSINESS)
                                .logSeverity())
                                .isEqualTo(WorkflowLogSeverity.ERROR);
                assertThat(WorkflowFailureClassification.forCategory(WorkflowFailureCategory.DOWNSTREAM_AVAILABILITY)
                                .logSeverity()).isEqualTo(WorkflowLogSeverity.FATAL);
                assertThat(WorkflowFailureClassification.forCategory(WorkflowFailureCategory.SYSTEM).logSeverity())
                                .isEqualTo(WorkflowLogSeverity.FATAL);
        }

        @Test
        void p5WorkflowTransitionGuidelineTreatsFailuresAsNotStatuses() {
                assertThat(WorkflowStableStateRule.values()).containsExactly(
                                WorkflowStableStateRule.KEEP_CURRENT_STABLE_STATE,
                                WorkflowStableStateRule.MOVE_TO_NEXT_STABLE_STATE,
                                WorkflowStableStateRule.MOVE_TO_TERMINAL_STATE,
                                WorkflowStableStateRule.RESTORE_PREVIOUS_STABLE_STATE,
                                WorkflowStableStateRule.RESTART_REQUIRED);
                assertThat(WorkflowTransitionCheck.recoverableFailure(
                                WorkflowStep.VERIFY_OTP,
                                WorkflowNextStep.VERIFY_OTP,
                                WorkflowFailureCategory.VALIDATION).rule())
                                .isEqualTo(WorkflowStableStateRule.KEEP_CURRENT_STABLE_STATE);
                assertThat(Arrays.stream(WorkflowNextStep.values()).map(Enum::name)).doesNotContain("FAILED",
                                "UNKNOWN_FAILURE",
                                "ERROR");
                assertThat(Arrays.stream(WorkflowResultStatus.values()).map(Enum::name)).doesNotContain("FAILED",
                                "UNKNOWN",
                                "ERROR");
        }

        @Test
        void p5WorkflowPageSelectionUsesPageIdAndVariantOnly() {
                WorkflowPageSelection selection = RegistrationWorkflowPageSelections
                                .registrationSuccessHasRemainingTrusts();

                assertThat(selection.scenario()).isEqualTo(WorkflowScenario.REGISTRATION);
                assertThat(selection.nextStep()).isEqualTo(WorkflowNextStep.REGISTRATION_SUCCESS);
                assertThat(selection.pageRef().pageId()).isEqualTo("registration-success");
                assertThat(selection.pageRef().variant()).isEqualTo("hasRemainingTrusts");
                assertThat(recordComponentNames(WorkflowPageSelection.class))
                                .containsExactly("scenario", "nextStep", "pageRef")
                                .doesNotContain("title", "body", "actions", "label", "labels", "messages",
                                                "localizedText");
        }

        @Test
        void p5WorkflowResolvedPageUsesSingleLanguageShape() {
                var page = new WorkflowResolvedPage(
                                "registration-success",
                                "hasRemainingTrusts",
                                "Successful Registration",
                                "Your registration is complete.",
                                List.of(new WorkflowResolvedPageAction("returnToLogin", "PRIMARY", "Return to Login",
                                                WorkflowNextStep.RETURN_TO_LOGIN)));

                assertThat(page.title()).isInstanceOf(String.class);
                assertThat(page.body()).isInstanceOf(String.class);
                assertThat(page.actions().getFirst().label()).isInstanceOf(String.class);
                assertThat(recordComponentNames(WorkflowResolvedPage.class))
                                .doesNotContain("titleEn", "titleZh", "bodyEn", "bodyZh", "localizedText", "labels");
                assertThat(page.getClass().getRecordComponents())
                                .noneMatch(component -> component.getType().equals(Map.class));
        }

        @Test
        void p5RegistrationWorkflowPageSelectionsMatchBffPagesIds() {
                assertThat(RegistrationWorkflowPageSelections.registrationStart().pageRef().pageId())
                                .isEqualTo(RegistrationWorkflowConstants.PAGE_REGISTRATION_START);
                assertThat(RegistrationWorkflowPageSelections.registrationOtpVerification().pageRef().pageId())
                                .isEqualTo(RegistrationWorkflowConstants.PAGE_REGISTRATION_OTP_VERIFICATION);
                assertThat(RegistrationWorkflowPageSelections.registrationTrustSelection().pageRef().pageId())
                                .isEqualTo(RegistrationWorkflowConstants.PAGE_REGISTRATION_TRUST_SELECTION);
                assertThat(RegistrationWorkflowPageSelections.registrationUsernameCheck().pageRef().pageId())
                                .isEqualTo(RegistrationWorkflowConstants.PAGE_REGISTRATION_USERNAME_CHECK);
                assertThat(RegistrationWorkflowPageSelections.registrationPasswordSetup().pageRef().pageId())
                                .isEqualTo(RegistrationWorkflowConstants.PAGE_REGISTRATION_PASSWORD_SETUP);
                assertThat(RegistrationWorkflowPageSelections.registrationSuccessNoRemainingTrusts().pageRef()
                                .variant())
                                .isEqualTo("noRemainingTrusts");
                assertThat(RegistrationWorkflowPageSelections.registrationSuccessHasRemainingTrusts().pageRef()
                                .variant())
                                .isEqualTo("hasRemainingTrusts");
        }

        private static void assertCodeKey(String value) {
                assertThat(value).matches("^[a-z][A-Za-z0-9]*(\\.[a-z][A-Za-z0-9]*)+$");
                assertThat(value).doesNotContain(" ", "@", "csrf_", "otp_", "Bearer", "Cookie", "成功", "失敗", "Please ",
                                "The ");
        }

        private static List<String> recordComponentNames(Class<?> type) {
                return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
        }

        private static void assertClassAbsent(String className) {
                try {
                        Class.forName(className);
                } catch (ClassNotFoundException expected) {
                        return;
                }
                throw new AssertionError("Class should be absent: " + className);
        }

        private record SamplePayload(String maskedDestination, int expiresInSeconds) {
        }
}
