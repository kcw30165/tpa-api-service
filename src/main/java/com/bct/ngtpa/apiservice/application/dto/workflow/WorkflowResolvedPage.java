package com.bct.ngtpa.apiservice.application.dto.workflow;

import java.util.List;
import java.util.Objects;

public record WorkflowResolvedPage(
        String pageId,
        String variant,
        String title,
        String body,
        List<WorkflowResolvedPageAction> actions) {
    public WorkflowResolvedPage {
        Objects.requireNonNull(pageId, "pageId must not be null");
        if (pageId.isBlank()) {
            throw new IllegalArgumentException("pageId must not be blank");
        }
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
