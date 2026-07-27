package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.LoginHttpResponses;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto.LoginOtpVerifyDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto.LoginOtpVerifyRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto.LoginWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper.LoginOtpVerifyWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.XsrfCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedCsrfCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.workflow.LoginErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.port.in.VerifyLoginOtpUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify.VerifyLoginOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify.VerifyLoginOtpStatus;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth/login/otp")
public final class AuthLoginOtpVerifyController {
    private final VerifyLoginOtpUseCase useCase;
    private final LoginOtpVerifyWebMapper mapper;
    private final AuthFlowCookieWriter authFlowCookies;
    private final XsrfCookieWriter xsrfCookies;
    private final AuthenticatedSessionCookieWriter authenticatedSessionCookies;
    private final AuthenticatedCsrfCookieWriter authenticatedCsrfCookies;

    public AuthLoginOtpVerifyController(
            VerifyLoginOtpUseCase useCase,
            LoginOtpVerifyWebMapper mapper,
            AuthFlowCookieWriter authFlowCookies,
            XsrfCookieWriter xsrfCookies,
            AuthenticatedSessionCookieWriter authenticatedSessionCookies,
            AuthenticatedCsrfCookieWriter authenticatedCsrfCookies) {
        this.useCase = useCase;
        this.mapper = mapper;
        this.authFlowCookies = authFlowCookies;
        this.xsrfCookies = xsrfCookies;
        this.authenticatedSessionCookies = authenticatedSessionCookies;
        this.authenticatedCsrfCookies = authenticatedCsrfCookies;
    }

    @PostMapping("/verify")
    public Mono<ResponseEntity<MutationResponse<LoginWorkflowResultResponse<LoginOtpVerifyDataResponse>>>> verify(
            @RequestBody(required = false) LoginOtpVerifyRequest request,
            @CookieValue(name = "auth_flow_sid", required = false) String session,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String csrf,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = RequestCorrelation.REQUEST_ID_HEADER, required = false) String requestId,
            @RequestHeader(name = RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, required = false) String language,
            @RequestHeader(name = "Origin", required = false) String origin,
            @RequestHeader(name = "Referer", required = false) String referer,
            ServerHttpResponse response) {
        if (hasText(requestId)) {
            response.getHeaders().set(RequestCorrelation.REQUEST_ID_HEADER, requestId.trim());
        }
        if (!hasText(session)) {
            return missing(LoginErrorKey.AUTH_FLOW_INVALID.value(), "PAGE");
        }
        if (!hasText(csrf)) {
            return missing(LoginErrorKey.CSRF_INVALID.value(), "PAGE");
        }
        if (!hasText(idempotencyKey)) {
            return missing(SharedWorkflowErrorKey.IDEMPOTENCY_MISSING.value(), "Idempotency-Key");
        }
        if (request == null || !hasText(request.code())) {
            return missing(LoginErrorKey.OTP_REQUIRED.value(), "otpCode");
        }
        try {
            return useCase.verify(new VerifyLoginOtpCommand(
                    session.trim(),
                    csrf.trim(),
                    request.code().trim(),
                    idempotencyKey.trim(),
                    clean(origin),
                    clean(referer),
                    clean(language),
                    clean(requestId)))
                    .map(result -> {
                        if (result.status() == VerifyLoginOtpStatus.ACCOUNTS_READY
                                && result.authenticatedSessionId() != null
                                && hasText(result.authenticatedCsrfToken())) {
                            authenticatedSessionCookies.write(
                                    response,
                                    result.authenticatedSessionId().value());
                            authenticatedCsrfCookies.write(
                                    response,
                                    result.authenticatedCsrfToken());
                        }
                        if (result.clearAuthFlowCookie()) {
                            authFlowCookies.clearAuthFlowCookie(response);
                            xsrfCookies.clearXsrfCookie(response);
                        }
                        return LoginHttpResponses.toResponse(mapper.toMutationResponse(result));
                    });
        } catch (RuntimeException exception) {
            return missing(LoginErrorKey.REQUEST_INVALID.value(), "PAGE");
        }
    }

    private Mono<ResponseEntity<MutationResponse<LoginWorkflowResultResponse<LoginOtpVerifyDataResponse>>>> missing(
            String code,
            String target) {
        return Mono.just(LoginHttpResponses.toResponse(mapper.missing(code, target)));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String clean(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
