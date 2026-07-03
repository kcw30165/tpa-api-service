package com.bct.ngtpa.apiservice.application.dto.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowPageSelectionTest {

    @Test
    void workflowPageSelectionCarriesScenarioNextStepAndPageRef() {
        var pageRef = new WorkflowPageRef("registration-success", "hasRemainingTrusts");
        var selection = new WorkflowPageSelection(
                WorkflowScenario.REGISTRATION,
                WorkflowNextStep.REGISTRATION_SUCCESS,
                pageRef);

        assertThat(selection.scenario()).isEqualTo(WorkflowScenario.REGISTRATION);
        assertThat(selection.nextStep()).isEqualTo(WorkflowNextStep.REGISTRATION_SUCCESS);
        assertThat(selection.pageRef()).isEqualTo(pageRef);
        assertThat(selection.hasPageRef()).isTrue();
    }

    @Test
    void workflowPageSelectionDoesNotContainLocalizedContent() {
        assertThat(recordComponentNames(WorkflowPageSelection.class))
                .containsExactly("scenario", "nextStep", "pageRef")
                .doesNotContain("title", "body", "actions", "label", "labels", "messages", "localizedText");
    }

    @Test
    void workflowPageResolutionCommandCarriesSelectionAndLanguageOnly() {
        var command = new WorkflowPageResolutionCommand(
                new WorkflowPageSelection(
                        WorkflowScenario.REGISTRATION,
                        WorkflowNextStep.REGISTRATION_SUCCESS,
                        new WorkflowPageRef("registration-success", "hasRemainingTrusts")),
                "en");

        assertThat(command.selection().pageRef().pageId()).isEqualTo("registration-success");
        assertThat(command.language()).isEqualTo("en");
        assertThat(recordComponentNames(WorkflowPageResolutionCommand.class))
                .containsExactly("selection", "language")
                .doesNotContain("headers", "request", "cookies", "sessionId", "authFlowSessionId", "accountRef", "csrfToken");
    }

    @Test
    void workflowResolvedPageContainsSingleLanguageFields() {
        var action = new WorkflowResolvedPageAction(
                "returnToLogin",
                "PRIMARY",
                "Return to Login",
                WorkflowNextStep.RETURN_TO_LOGIN);
        var page = new WorkflowResolvedPage(
                "registration-success",
                "hasRemainingTrusts",
                "Successful Registration",
                "Your registration is successful.",
                List.of(action));

        assertThat(page.pageId()).isEqualTo("registration-success");
        assertThat(page.variant()).isEqualTo("hasRemainingTrusts");
        assertThat(page.title()).isEqualTo("Successful Registration");
        assertThat(page.body()).isEqualTo("Your registration is successful.");
        assertThat(page.actions()).containsExactly(action);
        assertThat(recordComponentNames(WorkflowResolvedPage.class))
                .containsExactly("pageId", "variant", "title", "body", "actions")
                .doesNotContain("titleEn", "titleZh", "bodyEn", "bodyZh", "localizedText", "labels");
        assertThatThrownBy(() -> page.actions().add(action)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void workflowResolvedPageActionCarriesNextStep() {
        var action = new WorkflowResolvedPageAction(
                "returnToLogin",
                "PRIMARY",
                "Return to Login",
                WorkflowNextStep.RETURN_TO_LOGIN);

        assertThat(action.id()).isEqualTo("returnToLogin");
        assertThat(action.type()).isEqualTo("PRIMARY");
        assertThat(action.label()).isEqualTo("Return to Login");
        assertThat(action.nextStep()).isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
        assertThat(recordComponentNames(WorkflowResolvedPageAction.class))
                .containsExactly("id", "type", "label", "nextStep")
                .doesNotContain("labelEn", "labelZh", "localizedLabel", "href", "url");
    }

    private static List<String> recordComponentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }
}
