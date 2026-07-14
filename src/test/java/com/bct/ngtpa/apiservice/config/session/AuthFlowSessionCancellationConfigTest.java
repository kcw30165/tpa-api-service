package com.bct.ngtpa.apiservice.config.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.adapter.out.session.AuthFlowSessionCancellationAdapter;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import org.junit.jupiter.api.Test;

class AuthFlowSessionCancellationConfigTest {
    @Test
    void wiresTypedCancellationCapabilityFromExistingSessionPersistencePort() {
        var config = new AuthFlowSessionRuntimeServiceConfig();
        var capability = config.authFlowSessionCancellationPort(mock(AuthFlowSessionPort.class));
        assertThat(capability).isInstanceOf(AuthFlowSessionCancellationAdapter.class);
    }
}
