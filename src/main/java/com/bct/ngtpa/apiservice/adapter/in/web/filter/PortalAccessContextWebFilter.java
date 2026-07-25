package com.bct.ngtpa.apiservice.adapter.in.web.filter;

import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextPort;
import com.bct.ngtpa.apiservice.shared.web.*;

import lombok.RequiredArgsConstructor;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

/**
 * Resolves authenticated selected-account context from Account-Ref plus
 * sid_xxx.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public final class PortalAccessContextWebFilter implements WebFilter {
    private final PortalAccessContextPort portalAccessContextPort;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String accountRef = accountRef(exchange);
        if (!StringUtils.hasText(accountRef))
            return chain.filter(exchange);
        String sessionId = sessionId(exchange);
        if (!StringUtils.hasText(sessionId))
            return Mono.error(new IllegalStateException("Authenticated portal context unavailable"));
        return portalAccessContextPort.resolvePortalAccessContext(accountRef, sessionId)
                .flatMap(context -> continueWith(exchange, chain, context));
    }

    private Mono<Void> continueWith(ServerWebExchange exchange, WebFilterChain chain, PortalAccessContext context) {
        exchange.getAttributes().put(PortalAccessContextKeys.ATTRIBUTE_KEY, context);
        return chain.filter(exchange).contextWrite(c -> c.put(PortalAccessContextKeys.CONTEXT_KEY, context));
    }

    private String accountRef(ServerWebExchange exchange) {
        RequestHeaderContext c = (RequestHeaderContext) exchange.getAttributes()
                .get(RequestHeaderContextKeys.ATTRIBUTE_KEY);
        if (c != null && StringUtils.hasText(c.accountRef()))
            return c.accountRef();
        return exchange.getRequest().getHeaders().getFirst(RequestHeaderContextKeys.ACCOUNT_REF_HEADER);
    }

    private String sessionId(ServerWebExchange exchange) {
        return exchange.getRequest().getHeaders().getFirst(RequestHeaderContextKeys.AUTHENTICATED_SESSION_HEADER);
    }
}
