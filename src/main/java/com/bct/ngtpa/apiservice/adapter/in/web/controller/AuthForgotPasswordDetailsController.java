package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.*;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.*;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.*;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.in.ProcessForgotPasswordDetailsOtpUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.*;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import org.springframework.http.*;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth/forgot-password")
public final class AuthForgotPasswordDetailsController {
    private final ProcessForgotPasswordDetailsOtpUseCase useCase;
    private final ForgotPasswordDetailsOtpWebMapper mapper;

    public AuthForgotPasswordDetailsController(ProcessForgotPasswordDetailsOtpUseCase u,
            ForgotPasswordDetailsOtpWebMapper m) {
        useCase = u;
        mapper = m;
    }

    @PostMapping("/details")
    public Mono<ResponseEntity<?>> details(@RequestBody(required = false) ForgotPasswordDetailsRequest b,
            @CookieValue(name = "auth_flow_sid", required = false) String sid,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String csrf,
            @RequestHeader(name = "Origin", required = false) String origin,
            @RequestHeader(name = "Referer", required = false) String referer,
            @RequestHeader(name = RequestCorrelation.REQUEST_ID_HEADER, required = false) String requestId,
            @RequestHeader(name = "Accept-Language", required = false) String lang,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            ServerHttpResponse response) {
        echoRequestId(response, requestId);
        if (b == null || blank(sid) || blank(csrf) || blank(key))
            return Mono
                    .just(ForgotPasswordHttpResponses.toResponse(mapper.map(ForgotPasswordDetailsOtpResult.system())));
        try {
            return useCase
                    .process(new ForgotPasswordDetailsOtpCommand(new AuthFlowSessionId(sid), csrf, origin, referer,
                            requestId, lang, key, b.dateOfBirth(), b.email(), b.mobile()))
                    .map(r -> ForgotPasswordHttpResponses.toResponse(mapper.map(r)));
        } catch (RuntimeException e) {
            return Mono
                    .just(ForgotPasswordHttpResponses.toResponse(mapper.map(ForgotPasswordDetailsOtpResult.system())));
        }
    }

    private boolean blank(String v) {
        return v == null || v.isBlank();
    }

    private void echoRequestId(ServerHttpResponse response, String requestId) {
        if (!blank(requestId)) {
            response.getHeaders().set(RequestCorrelation.REQUEST_ID_HEADER, requestId.trim());
        }
    }
}
