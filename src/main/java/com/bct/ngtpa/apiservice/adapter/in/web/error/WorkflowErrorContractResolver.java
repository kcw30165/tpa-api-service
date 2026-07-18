package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowPageRef;
import java.util.Objects;
import org.springframework.util.StringUtils;

public final class WorkflowErrorContractResolver {
    private final WorkflowErrorContractProperties properties;

    public WorkflowErrorContractResolver(WorkflowErrorContractProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public ResolvedWorkflowErrorDefinition resolve(String code, String ignoredLanguage) {
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("Workflow error code must not be blank");
        }
        String normalizedCode = code.trim();
        var definition = properties.definitionsByCode().get(normalizedCode);
        if (definition == null) {
            throw new IllegalStateException("Workflow error definition is missing: " + normalizedCode);
        }
        return new ResolvedWorkflowErrorDefinition(
                normalizedCode,
                definition.getTargets(),
                definition.getType(),
                definition.getSeverity(),
                definition.getSource(),
                definition.getNextStep(),
                resolvePageRef(definition.getNextStep()));
    }

    private WorkflowPageRef resolvePageRef(WorkflowNextStep nextStep) {
        if (nextStep == null || nextStep == WorkflowNextStep.NONE) {
            return null;
        }
        var navigation = properties.getNavigation().get(nextStep);
        if (navigation == null || !StringUtils.hasText(navigation.getPageId())) {
            throw new IllegalStateException("Workflow navigation is missing: " + nextStep);
        }
        return new WorkflowPageRef(
                navigation.getPageId().trim(),
                StringUtils.hasText(navigation.getVariant()) ? navigation.getVariant().trim() : null);
    }
}
