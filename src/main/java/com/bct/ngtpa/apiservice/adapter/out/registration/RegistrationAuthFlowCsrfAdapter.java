package com.bct.ngtpa.apiservice.adapter.out.registration;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationCsrPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionCsrfService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CreateAuthFlowSessionCsrfCommand;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import reactor.core.publisher.Mono;

public class RegistrationAuthFlowCsrfAdapter implements RegistrationCsrPort {
    private final AuthFlowSessionCsrfService csrfService;
    private final Clock clock;
    private final int csrfTtlSeconds;

    public RegistrationAuthFlowCsrfAdapter(
            AuthFlowSessionCsrfService csrfService,
            Clock clock,
            int csrfTtlSeconds) {
        this.csrfService = Objects.requireNonNull(csrfService, "csrfService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.csrfTtlSeconds = csrfTtlSeconds > 0 ? csrfTtlSeconds : 1800;
    }

    @Override
    public Mono<String> bindCsrfToken(String authFlowSessionId) {
        var command = new CreateAuthFlowSessionCsrfCommand(
                new AuthFlowSessionId(authFlowSessionId),
                Instant.now(clock),
                csrfTtlSeconds,
                null);

        return csrfService
                .createForSession(command)
                .map(RegistrationAuthFlowCsrfAdapter::extractTokenValue);
    }

    private static String extractTokenValue(Object result) {
        Object token = firstValue(result, "rawToken", "token", "csrfToken", "value");
        if (token == null) {
            throw new IllegalStateException("Auth flow CSRF result does not contain a raw token");
        }
        return String.valueOf(token);
    }

    private static Object firstValue(Object target, String... accessors) {
        if (target == null) {
            return null;
        }

        for (String accessor : accessors) {
            try {
                Method method = target.getClass().getMethod(accessor);
                Object value = method.invoke(target);
                if (value != null) {
                    return value;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try next accessor.
            }
        }

        return null;
    }
}