package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.port.out.authflow.AuthFlowProtectedContextPort;

public interface ForgotPasswordAuthFlowContextProtectorPort
    extends AuthFlowProtectedContextPort<
        ForgotPasswordAuthFlowContext, ForgotPasswordAuthFlowEncryptedContext> {}
