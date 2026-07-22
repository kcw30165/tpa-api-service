package com.bct.ngtpa.apiservice.adapter.out.registration.compatibility;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.authflow.AuthFlowMessagePort;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Temporary one-way bridge retained until 0043-002-002 migrates registration. */
public final class RegistrationAuthFlowMessageCompatibilityAdapter
        implements AuthFlowMessagePort<RegistrationContactType> {
    private final RegistrationMessagePort delegate;

    public RegistrationAuthFlowMessageCompatibilityAdapter(RegistrationMessagePort delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public Mono<Boolean> deliver(
            RegistrationContactType channel,
            String recipient,
            String code,
            int ttlSeconds,
            String acceptLanguage,
            String correlationId) {
        return delegate.deliverRegistrationOtp(
                channel, recipient, code, ttlSeconds, acceptLanguage, correlationId);
    }
}
