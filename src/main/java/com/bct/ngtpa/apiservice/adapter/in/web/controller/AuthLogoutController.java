package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.logout.AuthenticatedLogoutWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedCsrfCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.usecase.auth.logout.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.*;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public final class AuthLogoutController {
    private final AuthenticatedLogoutUseCase useCase;
    private final AuthenticatedLogoutWebMapper mapper;
    private final ValidateAuthenticatedSessionUseCase sessionValidation;
    private final AuthenticatedSessionCookieWriter sessionCookies;
    private final AuthenticatedCsrfCookieWriter csrfCookies;

    @PostMapping("/logout")
    public Mono<ResponseEntity<MutationResponse<Void>>> logout(
            @RequestHeader(name = "X-XSRF-TOKEN", required = false) String csrf,
            @RequestHeader(name = RequestCorrelation.REQUEST_ID_HEADER, required = false) String requestId,
            ServerWebExchange exchange,
            ServerHttpResponse response) {
        response.getHeaders().setCacheControl(CacheControl.noStore());
        if (hasText(requestId)) response.getHeaders().set(RequestCorrelation.REQUEST_ID_HEADER, requestId.trim());
        String rawSession = cookie(exchange, sessionCookies.cookieName());
        if (!hasText(rawSession)) return idempotent(response);
        AuthenticatedSessionId id;
        try { id = new AuthenticatedSessionId(rawSession.trim()); }
        catch (RuntimeException failure) { return idempotent(response); }
        if (!hasText(csrf)) return unauthorized();
        return sessionValidation.validate(new ValidateAuthenticatedSessionCommand(id, csrf, clean(requestId)))
                .flatMap(ignored -> useCase.logout(new AuthenticatedLogoutCommand(id, clean(requestId))))
                .map(result -> {
                    if (result.successful()) clear(response);
                    return result.successful()
                            ? ResponseEntity.ok(mapper.toResponse(result))
                            : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(mapper.toResponse(result));
                })
                .onErrorResume(AuthenticatedPortalContextResolutionException.class, ignored -> unauthorized());
    }

    private Mono<ResponseEntity<MutationResponse<Void>>> idempotent(ServerHttpResponse response) {
        clear(response);
        return Mono.just(ResponseEntity.ok(mapper.idempotentSuccess()));
    }
    private Mono<ResponseEntity<MutationResponse<Void>>> unauthorized() {
        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                MutationResponse.failure(
                        com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus.BUSINESS_REJECTED,
                        java.util.List.of(new com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError(
                                "AUTHENTICATION_REQUIRED",
                                com.bct.ngtpa.apiservice.shared.error.ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED,
                                null, java.util.List.of("PAGE"), "ERROR", "SERVER")))));
    }
    private void clear(ServerHttpResponse response) { sessionCookies.clear(response); csrfCookies.clear(response); }
    private String cookie(ServerWebExchange exchange, String name) {
        var value = exchange.getRequest().getCookies().getFirst(name);
        return value == null ? null : value.getValue();
    }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String clean(String value) { return hasText(value) ? value.trim() : null; }
}
