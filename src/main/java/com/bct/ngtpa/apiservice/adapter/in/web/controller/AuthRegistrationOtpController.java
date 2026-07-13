package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationOtpVerifyRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpResendWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpVerifyWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.RegistrationIdempotencyProperties;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.in.ResendRegistrationOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.in.VerifyRegistrationOtpUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.ResendRegistrationOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpCommand;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth/registration/otp")
@RequiredArgsConstructor
public class AuthRegistrationOtpController {

    private final ResendRegistrationOtpUseCase resendRegistrationOtpUseCase;
    private final RegistrationOtpResendWebMapper registrationOtpResendMapper;
    private final VerifyRegistrationOtpUseCase verifyRegistrationOtpUseCase;
    private final RegistrationOtpVerifyWebMapper registrationOtpVerifyMapper;
    private final RegistrationIdempotencyProperties idempotencyProperties;

    @PostMapping(path = "/resend", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Object> resendOtp(
            @CookieValue(name = "auth_flow_sid", required = false) String authFlowSessionId,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String csrfToken,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = "X-Request-Id", required = false) String requestId,
            @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage,
            @RequestHeader(name = "Origin", required = false) String origin,
            @RequestHeader(name = "Referer", required = false) String referer) {
        if (!hasText(authFlowSessionId)) {
            return Mono.just(registrationOtpResendMapper.missingAuthFlowCookieResponse());
        }
        if (!hasText(csrfToken)) {
            return Mono.just(registrationOtpResendMapper.missingCsrfTokenResponse());
        }
        if (!hasText(idempotencyKey)) {
            return Mono.just(registrationOtpResendMapper.missingIdempotencyKeyResponse());
        }

        ResendRegistrationOtpCommand command;
        try {
            command = new ResendRegistrationOtpCommand(
                    new AuthFlowSessionId(authFlowSessionId.trim()),
                    csrfToken.trim(),
                    trimToNull(origin),
                    trimToNull(referer),
                    new RegistrationIdempotencyKey(idempotencyKey.trim()),
                    trimToNull(acceptLanguage),
                    trimToNull(requestId),
                    idempotencyProperties.otpResendTtl());
        } catch (RuntimeException exception) {
            return Mono.just(registrationOtpResendMapper.missingAuthFlowCookieResponse());
        }

        return resendRegistrationOtpUseCase.resend(command)
                .map(registrationOtpResendMapper::toMutationResponse);
    }

    @PostMapping(path = "/verify", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
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
            return Mono.just(registrationOtpVerifyMapper.missingAuthFlowCookieResponse());
        }
        if (!hasText(csrfToken)) {
            return Mono.just(registrationOtpVerifyMapper.missingCsrfTokenResponse());
        }
        if (!hasText(idempotencyKey)) {
            return Mono.just(registrationOtpVerifyMapper.missingIdempotencyKeyResponse());
        }
        if (request == null || !hasText(request.code())) {
            return Mono.just(registrationOtpVerifyMapper.missingCodeResponse());
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
                    idempotencyProperties.otpVerifyTtl());
        } catch (RuntimeException exception) {
            return Mono.just(registrationOtpVerifyMapper.missingAuthFlowCookieResponse());
        }

        return verifyRegistrationOtpUseCase.verify(command)
                .map(registrationOtpVerifyMapper::toMutationResponse);
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
