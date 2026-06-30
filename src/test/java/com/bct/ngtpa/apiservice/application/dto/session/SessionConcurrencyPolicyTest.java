package com.bct.ngtpa.apiservice.application.dto.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SessionConcurrencyPolicyTest {

    @Test
    void concurrencyPolicyDefinesExpectedValues() {
        assertThat(SessionConcurrencyMode.values())
                .containsExactly(SessionConcurrencyMode.SINGLE_ACTIVE_FLOW, SessionConcurrencyMode.MULTI_FLOW);
        assertThat(SessionOnNewFlowPolicy.values())
                .containsExactly(SessionOnNewFlowPolicy.REPLACE_EXISTING, SessionOnNewFlowPolicy.ALLOW_PARALLEL);

        var policy = new SessionConcurrencyPolicy(
                SessionConcurrencyMode.SINGLE_ACTIVE_FLOW,
                SessionOnNewFlowPolicy.REPLACE_EXISTING);
        assertThat(policy.mode()).isEqualTo(SessionConcurrencyMode.SINGLE_ACTIVE_FLOW);
        assertThat(policy.onNewFlow()).isEqualTo(SessionOnNewFlowPolicy.REPLACE_EXISTING);
    }

    @Test
    void concurrencyPolicyIsPassiveOnly() {
        assertClassAbsent("com.bct.ngtpa.apiservice.application.service.session.AuthFlowSessionLifecycleService");        
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.session.HttpAuthFlowCookieAdapter");
    }

    private static void assertClassAbsent(String className) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(className));
    }
}
