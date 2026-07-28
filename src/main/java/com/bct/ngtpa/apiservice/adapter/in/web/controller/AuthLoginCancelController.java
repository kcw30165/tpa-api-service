package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.*;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.cancel.*;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto.LoginWorkflowResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.*;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.in.CancelLoginUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.cancel.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth/login")
public final class AuthLoginCancelController {
    private final CancelLoginUseCase useCase;
    private final CancelLoginWebMapper mapper;
    private final AuthFlowCookieWriter authCookie;
    private final XsrfCookieWriter xsrfCookie;

    public AuthLoginCancelController(CancelLoginUseCase u, CancelLoginWebMapper m, AuthFlowCookieWriter a,
            XsrfCookieWriter x) {
        useCase = u;
        mapper = m;
        authCookie = a;
        xsrfCookie = x;
    }

    @PostMapping("/cancel")
    public Mono<ResponseEntity<MutationResponse<LoginWorkflowResultResponse<CancelLoginDataResponse>>>> cancel(
            @CookieValue(name = "auth_flow_sid", required = false) String session,
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String csrf,
            @RequestHeader(name = "X-Request-Id", required = false) String requestId,
            @RequestHeader(name = "Origin", required = false) String origin,
            @RequestHeader(name = "Referer", required = false) String referer, ServerHttpResponse response) {
        if (text(requestId))
            response.getHeaders().set("X-Request-Id", requestId.trim());
        if (!text(session)) {
            clear(response);
            return Mono.just(LoginHttpResponses.toResponse(mapper.missingSession()));
        }
        if (!text(csrf))
            return Mono.just(LoginHttpResponses.toResponse(mapper.missingCsrf()));
        try {
            return useCase.cancel(new CancelLoginCommand(new AuthFlowSessionId(session.trim()), csrf.trim(),
                    clean(origin), clean(referer), clean(requestId))).map(r -> {
                        if (r.clearAuthFlowCookie())
                            authCookie.clearAuthFlowCookie(response);
                        if (r.clearXsrfCookie())
                            xsrfCookie.clearXsrfCookie(response);
                        return LoginHttpResponses.toResponse(mapper.toMutationResponse(r));
                    });
        } catch (RuntimeException e) {
            return Mono.just(LoginHttpResponses.toResponse(mapper.missingCsrf()));
        }
    }

    private void clear(ServerHttpResponse r) {
        authCookie.clearAuthFlowCookie(r);
        xsrfCookie.clearXsrfCookie(r);
    }

    private boolean text(String v) {
        return v != null && !v.isBlank();
    }

    private String clean(String v) {
        return text(v) ? v.trim() : null;
    }
}
