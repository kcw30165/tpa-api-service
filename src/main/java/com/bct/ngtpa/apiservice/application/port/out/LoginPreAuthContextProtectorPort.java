package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.*;
import com.bct.ngtpa.apiservice.application.port.out.authflow.AuthFlowProtectedContextPort;

public interface LoginPreAuthContextProtectorPort
        extends AuthFlowProtectedContextPort<LoginPreAuthContext, LoginPreAuthEncryptedContext> {
}
