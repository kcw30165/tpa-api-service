package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationOtpVerifyRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpVerifyWebMapper;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpUseCase;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class AuthRegistrationOtpVerifyController {
    private static final Duration DEFAULT_IDEMPOTENCY_TTL = Duration.ofMinutes(5);
    private final VerifyRegistrationOtpUseCase verifyRegistrationOtpUseCase;
    private final RegistrationOtpVerifyWebMapper mapper;

    public AuthRegistrationOtpVerifyController(
            VerifyRegistrationOtpUseCase verifyRegistrationOtpUseCase,
            RegistrationOtpVerifyWebMapper mapper) {
        this.verifyRegistrationOtpUseCase = verifyRegistrationOtpUseCase;
        this.mapper = mapper;
    }

    @PostMapping(path = "/api/v1/auth/registration/otp/verify", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Object> verifyOtp(
            @RequestBody(required = false) RegistrationOtpVerifyRequest request,
            @CookieValue(name = "auth_flow_sid", required = false) String authFlowSessionId,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String csrfToken,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = "X-Request-Id", required = false) String requestId,
            @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage,
            @RequestHeader(name = "Origin", required = false) String origin,
            @RequestHeader(name = "Referer", required = false) String referer) {
        if (!hasText(authFlowSessionId)) {
            return Mono.just(mapper.missingAuthFlowCookieResponse());
        }
        if (!hasText(csrfToken)) {
            return Mono.just(mapper.missingCsrfTokenResponse());
        }
        if (!hasText(idempotencyKey)) {
            return Mono.just(mapper.missingIdempotencyKeyResponse());
        }
        if (request == null || !hasText(request.code())) {
            return Mono.just(mapper.missingCodeResponse());
        }

        VerifyRegistrationOtpCommand command;
        try {
            command = new VerifyRegistrationOtpCommand(
                    new AuthFlowSessionId(authFlowSessionId.trim()),
                    csrfToken.trim(),
                    request.code().trim(),
                    trimToNull(origin),
                    trimToNull(referer),
                    new RegistrationIdempotencyKey(idempotencyKey.trim()),
                    trimToNull(acceptLanguage),
                    trimToNull(requestId),
                    DEFAULT_IDEMPOTENCY_TTL);
        } catch (RuntimeException exception) {
            return Mono.just(mapper.missingAuthFlowCookieResponse());
        }

        return verifyRegistrationOtpUseCase.verify(command)
                .map(mapper::toMutationResponse);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
