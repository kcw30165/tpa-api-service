package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.port.out.authflow.AuthFlowProtectedContextPort;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowEncryptedContext;

public interface RegistrationAuthFlowContextProtectorPort extends AuthFlowProtectedContextPort<
        RegistrationAuthFlowContext,
        RegistrationAuthFlowEncryptedContext> {
    RegistrationAuthFlowEncryptedContext protect(RegistrationAuthFlowContext context);

    RegistrationAuthFlowContext unprotect(RegistrationAuthFlowEncryptedContext encryptedContext);
}
