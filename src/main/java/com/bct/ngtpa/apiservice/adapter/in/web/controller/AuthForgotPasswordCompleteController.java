package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.ForgotPasswordHttpResponses;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordCompleteDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordCompleteRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordCompleteWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.XsrfCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.port.in.CompleteForgotPasswordUseCase;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
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
@RequestMapping("/api/v1/auth/forgot-password")
public final class AuthForgotPasswordCompleteController {
    private final CompleteForgotPasswordUseCase useCase;
    private final ForgotPasswordCompleteWebMapper mapper;
    private final AuthFlowCookieWriter authFlowCookies;
    private final XsrfCookieWriter xsrfCookies;

    public AuthForgotPasswordCompleteController(
            CompleteForgotPasswordUseCase useCase,
            ForgotPasswordCompleteWebMapper mapper,
            AuthFlowCookieWriter authFlowCookies,
            XsrfCookieWriter xsrfCookies) {
        this.useCase = useCase;
        this.mapper = mapper;
        this.authFlowCookies = authFlowCookies;
        this.xsrfCookies = xsrfCookies;
    }

    @PostMapping(path = "/complete", consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE, produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<MutationResponse<ForgotPasswordWorkflowResultResponse<ForgotPasswordCompleteDataResponse>>>> complete(
            @RequestBody(required = false) ForgotPasswordCompleteRequest request,
            @CookieValue(name = "auth_flow_sid", required = false) String sessionId,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String csrfToken,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = RequestCorrelation.REQUEST_ID_HEADER, required = false) String requestId,
            @RequestHeader(name = "Accept-Language", required = false) String language,
            @RequestHeader(name = "Origin", required = false) String origin,
            @RequestHeader(name = "Referer", required = false) String referer,
            ServerHttpResponse response) {
        echoRequestId(response, requestId);
        if (!hasText(sessionId)) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(mapper.missingAuthFlowCookie()));
        }
        if (!hasText(csrfToken)) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(mapper.missingCsrfToken()));
        }
        if (!hasText(idempotencyKey)) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(mapper.missingIdempotencyKey()));
        }
        if (request == null) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(mapper.invalidRequest()));
        }
        try {
            return useCase.complete(mapper.toCommand(
                    request,
                    sessionId,
                    csrfToken,
                    idempotencyKey,
                    origin,
                    referer,
                    language,
                    requestId))
                    .map(result -> {
                        if (result.clearAuthFlowCookie()) {
                            authFlowCookies.clearAuthFlowCookie(response);
                        }
                        if (result.clearXsrfCookie()) {
                            xsrfCookies.clearXsrfCookie(response);
                        }
                        return ForgotPasswordHttpResponses.toResponse(mapper.toResponse(result));
                    })
                    .onErrorResume(error -> Mono.just(ForgotPasswordHttpResponses.toResponse(mapper.systemUnavailable())));
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