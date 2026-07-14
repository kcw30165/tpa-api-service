package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCancellationReason;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;

/** Typed cancellation capability for pre-authentication workflow sessions. */
public interface AuthFlowSessionCancellationPort
        extends SessionCancellationPort<AuthFlowSessionId, AuthFlowSessionCancellationReason> {
}
