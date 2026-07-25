package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.logout.AuthenticatedLogoutWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.usecase.auth.logout.AuthenticatedLogoutCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.logout.AuthenticatedLogoutUseCase;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public final class AuthLogoutController {
    private final AuthenticatedLogoutUseCase useCase;
    private final AuthenticatedLogoutWebMapper mapper;

    @PostMapping("/logout")
    public Mono<ResponseEntity<MutationResponse<Void>>> logout(
            @RequestHeader(name = RequestHeaderContextKeys.AUTHENTICATED_SESSION_HEADER, required = false)
                    String authenticatedSessionId,
            @RequestHeader(name = RequestCorrelation.REQUEST_ID_HEADER, required = false)
                    String requestId,
            ServerHttpResponse response) {
        response.getHeaders().setCacheControl(CacheControl.noStore());
        if (hasText(requestId)) {
            response.getHeaders().set(RequestCorrelation.REQUEST_ID_HEADER, requestId.trim());
        }

        AuthenticatedSessionId sessionId;
        try {
            if (!hasText(authenticatedSessionId)) {
                return Mono.just(ResponseEntity.ok(mapper.idempotentSuccess()));
            }
            sessionId = new AuthenticatedSessionId(authenticatedSessionId.trim());
        } catch (RuntimeException ignored) {
            return Mono.just(ResponseEntity.ok(mapper.idempotentSuccess()));
        }

        return useCase.logout(new AuthenticatedLogoutCommand(sessionId, clean(requestId)))
                .map(result -> result.successful()
                        ? ResponseEntity.ok(mapper.toResponse(result))
                        : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                .body(mapper.toResponse(result)));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String clean(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
