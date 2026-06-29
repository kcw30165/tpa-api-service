package com.bct.ngtpa.apiservice.application.dto.session;

import java.util.Objects;

public record SessionConcurrencyPolicy(SessionConcurrencyMode mode, SessionOnNewFlowPolicy onNewFlow) {
    public SessionConcurrencyPolicy {
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(onNewFlow, "onNewFlow must not be null");
    }
}
