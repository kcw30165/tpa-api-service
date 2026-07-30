package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyCompleteCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyFailCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStartResult;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyUnknownOutcomeCommand;
import com.bct.ngtpa.apiservice.application.port.out.authflow.AuthFlowIdempotencyPort;

public interface ForgotPasswordIdempotencyPort extends
        AuthFlowIdempotencyPort<ForgotPasswordIdempotencyAction, ForgotPasswordIdempotencyKey, ForgotPasswordIdempotencyStartCommand, ForgotPasswordIdempotencyStartResult, ForgotPasswordIdempotencyCompleteCommand, ForgotPasswordIdempotencyFailCommand, ForgotPasswordIdempotencyUnknownOutcomeCommand, ForgotPasswordIdempotencyRecord> {
}