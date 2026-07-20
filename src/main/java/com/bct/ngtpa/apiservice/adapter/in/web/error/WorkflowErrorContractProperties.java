package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workflow-error-contract")
@Getter
@Setter
public class WorkflowErrorContractProperties {
    private List<Definition> errors = new ArrayList<>();
    private Map<WorkflowNextStep, Navigation> navigation = new LinkedHashMap<>();

    public void setErrors(List<Definition> errors) {
        this.errors = errors == null ? new ArrayList<>() : new ArrayList<>(errors);
    }

    public void setNavigation(Map<WorkflowNextStep, Navigation> navigation) {
        this.navigation = navigation == null ? new LinkedHashMap<>() : new LinkedHashMap<>(navigation);
    }

    public Map<String, Definition> definitionsByCode() {
        Map<String, Definition> indexed = new LinkedHashMap<>();
        for (Definition definition : errors) {
            if (definition == null || definition.getCode() == null || definition.getCode().isBlank()) {
                continue;
            }
            String code = definition.getCode().trim();
            if (indexed.putIfAbsent(code, definition) != null) {
                throw new IllegalStateException("Duplicate workflow error definition: " + code);
            }
        }
        return Map.copyOf(indexed);
    }

    @Getter
    @Setter
    public static class Definition {
        private String code;
        private WorkflowErrorType type;
        private WorkflowErrorSeverity severity;
        private String source;
        private List<String> targets = new ArrayList<>();
        private WorkflowNextStep nextStep;

        public void setTargets(List<String> targets) {
            this.targets = targets == null ? new ArrayList<>() : new ArrayList<>(targets);
        }
    }

    @Getter
    @Setter
    public static class Navigation {
        private String pageId;
        private String variant;
    }
}
