package com.bct.ngtpa.apiservice.adapter.in.web.filter;

import com.bct.ngtpa.apiservice.adapter.in.web.response.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedPortalContextResolutionException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public final class AuthenticatedPortalContextWebExceptionHandler implements WebExceptionHandler {
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable failure) {
        if (!(failure instanceof AuthenticatedPortalContextResolutionException))
            return Mono.error(failure);
        MutationResponse<Void> body = MutationResponse.failure(ApiStatus.BUSINESS_REJECTED, null,
                List.of(new ApiError("AUTHENTICATION_REQUIRED", ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED, null,
                        List.of("PAGE"), "ERROR", "SERVER")));
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(objectMapper.writeValueAsBytes(body));
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception ignored) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }
}
