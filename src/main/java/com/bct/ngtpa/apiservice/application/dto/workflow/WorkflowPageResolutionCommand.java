package com.bct.ngtpa.apiservice.application.dto.workflow;

import java.util.Objects;

public record WorkflowPageResolutionCommand(WorkflowPageSelection selection, String language) {
    public WorkflowPageResolutionCommand {
        Objects.requireNonNull(selection, "selection must not be null");
        if (language != null) {
            language = language.trim();
        }
    }
}
