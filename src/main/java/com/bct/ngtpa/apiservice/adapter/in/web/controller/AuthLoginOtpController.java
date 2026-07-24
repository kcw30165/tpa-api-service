package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.*;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.otp.*;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto.LoginWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.auth.login.otp.SendLoginOtpCommand;
import com.bct.ngtpa.apiservice.application.dto.workflow.*;
import com.bct.ngtpa.apiservice.application.port.in.SendLoginOtpUseCase;
import org.springframework.http.*;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth/login/otp")
public final class AuthLoginOtpController {
    private final SendLoginOtpUseCase useCase;
    private final LoginOtpSendWebMapper mapper;

    public AuthLoginOtpController(SendLoginOtpUseCase useCase, LoginOtpSendWebMapper mapper) {
        this.useCase = useCase;
        this.mapper = mapper;
    }

    @PostMapping("/send")
    public Mono<ResponseEntity<MutationResponse<LoginWorkflowResultResponse<LoginOtpSendDataResponse>>>> send(
            @RequestBody(required = false) LoginOtpSendRequest request,
            @CookieValue(name = "auth_flow_sid", required = false) String session,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String csrf,
            @RequestHeader(name = "Idempotency-Key", required = false) String idem,
            @RequestHeader(name = "X-Request-Id", required = false) String requestId,
            @RequestHeader(name = "Accept-Language", required = false) String lang,
            @RequestHeader(name = "Origin", required = false) String origin,
            @RequestHeader(name = "Referer", required = false) String referer, ServerHttpResponse response) {
        if (text(requestId))
            response.getHeaders().set("X-Request-Id", requestId.trim());
        if (!text(session))
            return just(LoginErrorKey.AUTH_FLOW_INVALID.value(), "PAGE");
        if (!text(csrf))
            return just(LoginErrorKey.CSRF_INVALID.value(), "PAGE");
        if (!text(idem))
            return just(SharedWorkflowErrorKey.IDEMPOTENCY_MISSING.value(), "Idempotency-Key");
        if (!text(requestId))
            return just("err.request.id.missing", "X-Request-Id");
        if (request == null || !text(request.destinationId()))
            return just(LoginErrorKey.OTP_DESTINATION_INVALID.value(), "destinationId");
        try {
            return useCase
                    .send(new SendLoginOtpCommand(session.trim(), csrf.trim(), request.destinationId().trim(),
                            idem.trim(), clean(origin), clean(referer), clean(lang), requestId.trim()))
                    .map(mapper::toMutationResponse).map(LoginHttpResponses::toResponse)
                    .onErrorResume(e -> Mono.just(LoginHttpResponses
                            .toResponse(mapper.missing(SharedWorkflowErrorKey.SYSTEM_UNAVAILABLE.value(), "PAGE"))));
        } catch (RuntimeException e) {
            return just(LoginErrorKey.REQUEST_INVALID.value(), "PAGE");
        }
    }

    private Mono<ResponseEntity<MutationResponse<LoginWorkflowResultResponse<LoginOtpSendDataResponse>>>> just(
            String code, String target) {
        return Mono.just(LoginHttpResponses.toResponse(mapper.missing(code, target)));
    }

    private boolean text(String v) {
        return v != null && !v.isBlank();
    }

    private String clean(String v) {
        return text(v) ? v.trim() : null;
    }
}
