package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.*;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.*;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.*;
import com.bct.ngtpa.apiservice.application.port.in.VerifyForgotPasswordIdentityUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity.*;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import org.springframework.http.*;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth/forgot-password")
public final class AuthForgotPasswordIdentityController {
    private final VerifyForgotPasswordIdentityUseCase useCase;
    private final ForgotPasswordIdentityWebMapper mapper;

    public AuthForgotPasswordIdentityController(VerifyForgotPasswordIdentityUseCase u,
            ForgotPasswordIdentityWebMapper m) {
        useCase = u;
        mapper = m;
    }

    @PostMapping("/identity-check")
    public Mono<ResponseEntity<?>> check(@RequestBody(required = false) ForgotPasswordIdentityCheckRequest body,
            @CookieValue(name = "auth_flow_sid", required = false) String sid,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String csrf,
            @RequestHeader(name = "Origin", required = false) String origin,
            @RequestHeader(name = "Referer", required = false) String referer,
            @RequestHeader(name = RequestCorrelation.REQUEST_ID_HEADER, required = false) String requestId,
            @RequestHeader(name = "Accept-Language", required = false) String language,
            ServerHttpResponse response) {
        echoRequestId(response, requestId);
        if (sid == null || sid.isBlank() || csrf == null || csrf.isBlank())
            return Mono.just(ForgotPasswordHttpResponses.toResponse(mapper.response(
                    VerifyForgotPasswordIdentityResult.of(VerifyForgotPasswordIdentityStatus.SYSTEM_FAILED))));
        try {
            return useCase.verify(mapper.command(body, sid, csrf, origin, referer, requestId))
                    .map(r -> ForgotPasswordHttpResponses.toResponse(mapper.response(r)));
        } catch (RuntimeException e) {
            return Mono.just(ForgotPasswordHttpResponses.toResponse(mapper.response(
                    VerifyForgotPasswordIdentityResult.of(VerifyForgotPasswordIdentityStatus.SYSTEM_FAILED))));
        }
    }

    private void echoRequestId(ServerHttpResponse response, String requestId) {
        if (requestId != null && !requestId.isBlank()) {
            response.getHeaders().set(RequestCorrelation.REQUEST_ID_HEADER, requestId.trim());
        }
    }
}
