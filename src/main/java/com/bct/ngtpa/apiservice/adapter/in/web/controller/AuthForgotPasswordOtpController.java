package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.ForgotPasswordHttpResponses;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordOtpDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordOtpResendWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.in.ResendForgotPasswordOtpUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpCommand;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth/forgot-password/otp")
public final class AuthForgotPasswordOtpController {
    private final ResendForgotPasswordOtpUseCase useCase;
    private final ForgotPasswordOtpResendWebMapper mapper;

    public AuthForgotPasswordOtpController(ResendForgotPasswordOtpUseCase useCase,
            ForgotPasswordOtpResendWebMapper mapper) {
        this.useCase = useCase;
        this.mapper = mapper;
    }

    @PostMapping("/resend")
    public Mono<ResponseEntity<MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordOtpDataResponse>>>> resend(
            @CookieValue(name = "auth_flow_sid", required = false) String sessionId,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String csrfToken,
            @RequestHeader(name = "Origin", required = false) String origin,
            @RequestHeader(name = "Referer", required = false) String referer,
            @RequestHeader(name = RequestCorrelation.REQUEST_ID_HEADER, required = false) String requestId,
            @RequestHeader(name = "Accept-Language", required = false) String language,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            ServerHttpResponse response) {
        echoRequestId(response, requestId);
        if (!hasText(idempotencyKey)) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(mapper.missingIdempotencyKey()));
        }
        if (!hasText(sessionId) || !hasText(csrfToken)) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(mapper.systemUnavailable()));
        }
        try {
            return useCase.resend(new ResendForgotPasswordOtpCommand(
                    new AuthFlowSessionId(sessionId.trim()),
                    csrfToken.trim(),
                    clean(origin),
                    clean(referer),
                    clean(requestId),
                    clean(language),
                    idempotencyKey.trim()))
                    .map(mapper::toMutationResponse)
                    .map(ForgotPasswordHttpResponses::toResponse)
                    .onErrorResume(exception -> Mono.just(ForgotPasswordHttpResponses.toResponse(
                            mapper.systemUnavailable())));
        } catch (RuntimeException exception) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(mapper.systemUnavailable()));
        }
    }

    private void echoRequestId(ServerHttpResponse response, String requestId) {
        if (hasText(requestId)) {
            response.getHeaders().set(RequestCorrelation.REQUEST_ID_HEADER, requestId.trim());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String clean(String value) {
        return hasText(value) ? value.trim() : null;
    }
}