package com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context;

import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionExpiryRefreshPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class SlidingForgotPasswordProtectedAuthFlowContextLoader
        implements ForgotPasswordProtectedAuthFlowContextLoader {

    private final ForgotPasswordProtectedAuthFlowContextLoader delegate;
    private final AuthFlowSessionExpiryRefreshPort expiryRefreshPort;
    private final Clock clock;

    public SlidingForgotPasswordProtectedAuthFlowContextLoader(
            ForgotPasswordProtectedAuthFlowContextLoader delegate,
            AuthFlowSessionExpiryRefreshPort expiryRefreshPort,
            Clock clock) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.expiryRefreshPort = Objects.requireNonNull(expiryRefreshPort, "expiryRefreshPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public Mono<ForgotPasswordProtectedAuthFlowContextResult> load(
            ForgotPasswordProtectedAuthFlowContextCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return delegate.load(command)
                .flatMap(result -> {
                    if (!result.isValid()) {
                        return Mono.just(result);
                    }
                    Instant interactionAt = clock.instant();
                    return expiryRefreshPort.refresh(command.authFlowSessionId(), interactionAt).thenReturn(result);
                });
    }
}