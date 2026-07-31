package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.ForgotPasswordHttpResponses;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordStartDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.port.in.StartForgotPasswordUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordResultStatus;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/forgot-password")
public class AuthForgotPasswordController {
    private final StartForgotPasswordUseCase useCase;
    private final ForgotPasswordStartWebMapper mapper;
    private final AuthFlowCookieWriter cookieWriter;

    @PostMapping("/start")
    public Mono<ResponseEntity<MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordStartDataResponse>>>> startForgotPassword(
            @RequestBody(required = false) ForgotPasswordStartRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = RequestCorrelation.REQUEST_ID_HEADER, required = false) String requestId,
            @RequestHeader(name = "Accept-Language", required = false) String acceptLanguage,
            @CookieValue(name = "auth_flow_sid", required = false) String authFlowSessionId,
            ServerHttpResponse response) {
        echoRequestId(response, requestId);
        if (!hasText(idempotencyKey)) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(mapper.missingIdempotencyKey()));
        }
        if (request == null) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(mapper.invalidRequest()));
        }
        try {
            return useCase.start(mapper.toCommand(
                    request,
                    idempotencyKey.trim(),
                    requestId,
                    acceptLanguage,
                    authFlowSessionId))
                    .map(result -> {
                        if (result.status() == StartForgotPasswordResultStatus.STARTED
                                || result.status() == StartForgotPasswordResultStatus.COMPLETED_REPLAY) {
                            cookieWriter.writeAuthFlowCookie(response, result.authFlowSessionId());
                        }
                        return ForgotPasswordHttpResponses.toResponse(
                                mapper.toMutationResponse(result, acceptLanguage));
                    })
                    .onErrorResume(exception -> Mono.just(
                            ForgotPasswordHttpResponses.toResponse(mapper.systemUnavailable())));
        } catch (RuntimeException exception) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(mapper.invalidRequest()));
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
}