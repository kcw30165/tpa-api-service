package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.*;
import com.bct.ngtpa.apiservice.application.port.out.authflow.AuthFlowIdempotencyPort;

public interface LoginCredentialIdempotencyPort
    extends AuthFlowIdempotencyPort<
        LoginIdempotencyAction,
        LoginIdempotencyKey,
        LoginIdempotencyStartCommand,
        LoginIdempotencyStartResult,
        LoginIdempotencyCompleteCommand,
        LoginIdempotencyFailCommand,
        LoginIdempotencyUnknownOutcomeCommand,
        LoginIdempotencyRecord> {}
