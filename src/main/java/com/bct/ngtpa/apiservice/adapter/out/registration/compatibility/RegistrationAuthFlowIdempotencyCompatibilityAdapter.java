package com.bct.ngtpa.apiservice.adapter.out.registration.compatibility;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyCompleteCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyFailCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartResult;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyUnknownOutcomeCommand;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.authflow.AuthFlowIdempotencyPort;
import java.util.Objects;
import java.util.Optional;
import reactor.core.publisher.Mono;

/** Temporary one-way bridge retained until 0043-002-002 migrates registration. */
public final class RegistrationAuthFlowIdempotencyCompatibilityAdapter implements AuthFlowIdempotencyPort<
        RegistrationIdempotencyAction,
        RegistrationIdempotencyKey,
        RegistrationIdempotencyStartCommand,
        RegistrationIdempotencyStartResult,
        RegistrationIdempotencyCompleteCommand,
        RegistrationIdempotencyFailCommand,
        RegistrationIdempotencyUnknownOutcomeCommand,
        RegistrationIdempotencyRecord> {
    private final RegistrationIdempotencyPort delegate;

    public RegistrationAuthFlowIdempotencyCompatibilityAdapter(RegistrationIdempotencyPort delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override public Mono<RegistrationIdempotencyStartResult> startProcessing(RegistrationIdempotencyStartCommand command) { return delegate.startProcessing(command); }
    @Override public Mono<RegistrationIdempotencyRecord> markCompleted(RegistrationIdempotencyCompleteCommand command) { return delegate.markCompleted(command); }
    @Override public Mono<RegistrationIdempotencyRecord> markFailed(RegistrationIdempotencyFailCommand command) { return delegate.markFailed(command); }
    @Override public Mono<RegistrationIdempotencyRecord> markUnknownOutcome(RegistrationIdempotencyUnknownOutcomeCommand command) { return delegate.markUnknownOutcome(command); }
    @Override public Mono<Optional<RegistrationIdempotencyRecord>> load(RegistrationIdempotencyAction action, RegistrationIdempotencyKey key) { return delegate.load(action, key); }
    @Override public Mono<Void> delete(RegistrationIdempotencyAction action, RegistrationIdempotencyKey key) { return delegate.delete(action, key); }
}
