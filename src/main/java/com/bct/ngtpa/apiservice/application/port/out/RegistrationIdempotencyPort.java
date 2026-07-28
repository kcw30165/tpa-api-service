package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.port.out.authflow.AuthFlowIdempotencyPort;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyCompleteCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyFailCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyRecord;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartResult;
import java.util.Optional;
import reactor.core.publisher.Mono;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyUnknownOutcomeCommand;

public interface RegistrationIdempotencyPort extends AuthFlowIdempotencyPort<
        RegistrationIdempotencyAction,
        RegistrationIdempotencyKey,
        RegistrationIdempotencyStartCommand,
        RegistrationIdempotencyStartResult,
        RegistrationIdempotencyCompleteCommand,
        RegistrationIdempotencyFailCommand,
        RegistrationIdempotencyUnknownOutcomeCommand,
        RegistrationIdempotencyRecord> {
    Mono<RegistrationIdempotencyStartResult> startProcessing(RegistrationIdempotencyStartCommand command);

    Mono<RegistrationIdempotencyRecord> markCompleted(RegistrationIdempotencyCompleteCommand command);

    Mono<RegistrationIdempotencyRecord> markFailed(RegistrationIdempotencyFailCommand command);

    default Mono<RegistrationIdempotencyRecord> markUnknownOutcome(
            RegistrationIdempotencyUnknownOutcomeCommand command) {
        return Mono.error(new UnsupportedOperationException(
                "Registration idempotency unknown outcome is not supported"));
    }

    Mono<Optional<RegistrationIdempotencyRecord>> load(
            RegistrationIdempotencyAction action,
            RegistrationIdempotencyKey idempotencyKey);

    Mono<Void> delete(
            RegistrationIdempotencyAction action,
            RegistrationIdempotencyKey idempotencyKey);
}
