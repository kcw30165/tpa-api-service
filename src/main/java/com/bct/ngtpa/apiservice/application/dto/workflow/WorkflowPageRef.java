package com.bct.ngtpa.apiservice.application.dto.workflow;

import java.util.Objects;

public record WorkflowPageRef(String pageId, String variant) {
    public WorkflowPageRef {
        Objects.requireNonNull(pageId, "pageId must not be null");
        if (pageId.isBlank()) {
            throw new IllegalArgumentException("pageId must not be blank");
        }
    }
}
