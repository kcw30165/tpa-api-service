package com.bct.ngtpa.apiservice.adapter.out.registration.compatibility;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.authflow.AuthFlowProtectedContextPort;
import java.util.Objects;

/** Temporary one-way bridge retained until 0043-002-002 migrates registration. */
public final class RegistrationAuthFlowProtectedContextCompatibilityAdapter implements
        AuthFlowProtectedContextPort<RegistrationAuthFlowContext, RegistrationAuthFlowEncryptedContext> {
    private final RegistrationAuthFlowContextProtectorPort delegate;

    public RegistrationAuthFlowProtectedContextCompatibilityAdapter(
            RegistrationAuthFlowContextProtectorPort delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override public RegistrationAuthFlowEncryptedContext protect(RegistrationAuthFlowContext context) { return delegate.protect(context); }
    @Override public RegistrationAuthFlowContext unprotect(RegistrationAuthFlowEncryptedContext encryptedContext) { return delegate.unprotect(encryptedContext); }
}
