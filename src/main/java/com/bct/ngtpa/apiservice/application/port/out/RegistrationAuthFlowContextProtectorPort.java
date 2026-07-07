package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowEncryptedContext;

public interface RegistrationAuthFlowContextProtectorPort {
    RegistrationAuthFlowEncryptedContext protect(RegistrationAuthFlowContext context);

    RegistrationAuthFlowContext unprotect(RegistrationAuthFlowEncryptedContext encryptedContext);
}
