package com.bct.ngtpa.apiservice.application.dto.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkflowFailureClassificationTest {

    @Test
    void workflowFailureCategoryDefinesExpectedValues() {
        assertThat(WorkflowFailureCategory.values()).containsExactly(
                WorkflowFailureCategory.VALIDATION,
                WorkflowFailureCategory.BUSINESS,
                WorkflowFailureCategory.SECURITY,
                WorkflowFailureCategory.DOWNSTREAM_BUSINESS,
                WorkflowFailureCategory.DOWNSTREAM_AVAILABILITY,
                WorkflowFailureCategory.SYSTEM);
    }

    @Test
    void workflowLogSeverityDefinesExpectedValues() {
        assertThat(WorkflowLogSeverity.values()).containsExactly(
                WorkflowLogSeverity.INFO,
                WorkflowLogSeverity.WARN,
                WorkflowLogSeverity.ERROR,
                WorkflowLogSeverity.FATAL);
    }

    @Test
    void validationBusinessAndSecurityFailuresMapToErrorSeverity() {
        assertThat(WorkflowFailureClassification.forCategory(WorkflowFailureCategory.VALIDATION).logSeverity())
                .isEqualTo(WorkflowLogSeverity.ERROR);
        assertThat(WorkflowFailureClassification.forCategory(WorkflowFailureCategory.BUSINESS).logSeverity())
                .isEqualTo(WorkflowLogSeverity.ERROR);
        assertThat(WorkflowFailureClassification.forCategory(WorkflowFailureCategory.SECURITY).logSeverity())
                .isEqualTo(WorkflowLogSeverity.ERROR);
        assertThat(WorkflowFailureClassification.forCategory(WorkflowFailureCategory.DOWNSTREAM_BUSINESS).logSeverity())
                .isEqualTo(WorkflowLogSeverity.ERROR);
    }

    @Test
    void systemAndDownstreamAvailabilityFailuresMapToFatalSeverity() {
        assertThat(WorkflowFailureClassification.forCategory(WorkflowFailureCategory.SYSTEM).logSeverity())
                .isEqualTo(WorkflowLogSeverity.FATAL);
        assertThat(WorkflowFailureClassification.forCategory(WorkflowFailureCategory.DOWNSTREAM_AVAILABILITY).logSeverity())
                .isEqualTo(WorkflowLogSeverity.FATAL);
    }
}
