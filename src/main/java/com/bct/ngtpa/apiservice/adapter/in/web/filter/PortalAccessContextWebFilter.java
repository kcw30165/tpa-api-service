package com.bct.ngtpa.apiservice.adapter.in.web.filter;

import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextPort;
import com.bct.ngtpa.apiservice.shared.web.PortalAccessContextKeys;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Resolves the selected-account PortalAccessContext once per request.
 *
 * <p>Current temporary implementation resolves by Account-Ref against the YAML-backed
 * PortalAccessContextPort. Future implementation will resolve by HttpOnly session-id
 * cookie + Account-Ref header against Redis session state.</p>
 *
 * <p>If Account-Ref is absent, the filter skips resolution. Account-scoped controllers
 * and use cases continue to fail explicitly when they require context.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class PortalAccessContextWebFilter implements WebFilter {

    private final PortalAccessContextPort portalAccessContextPort;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String accountRef = resolveAccountRef(exchange);
        if (!StringUtils.hasText(accountRef)) {
            return chain.filter(exchange);
        }
        return portalAccessContextPort.resolvePortalAccessContext(accountRef, resolveSessionId(exchange))
                .flatMap(portalAccessContext -> continueWithPortalAccessContext(
                        exchange,
                        chain,
                        portalAccessContext));
    }

    private Mono<Void> continueWithPortalAccessContext(
            ServerWebExchange exchange,
            WebFilterChain chain,
            PortalAccessContext portalAccessContext) {
        exchange.getAttributes().put(PortalAccessContextKeys.ATTRIBUTE_KEY, portalAccessContext);
        return chain.filter(exchange)
                .contextWrite(context -> context.put(PortalAccessContextKeys.CONTEXT_KEY, portalAccessContext));
    }

    private String resolveAccountRef(ServerWebExchange exchange) {
        RequestHeaderContext requestHeaderContext = (RequestHeaderContext) exchange.getAttributes()
                .get(RequestHeaderContextKeys.ATTRIBUTE_KEY);
        if (requestHeaderContext != null && StringUtils.hasText(requestHeaderContext.accountRef())) {
            return requestHeaderContext.accountRef();
        }
        return exchange.getRequest().getHeaders().getFirst(RequestHeaderContextKeys.ACCOUNT_REF_HEADER);
    }
    private String resolveSessionId(ServerWebExchange exchange) {
        RequestHeaderContext requestHeaderContext = (RequestHeaderContext) exchange.getAttributes()
                .get(RequestHeaderContextKeys.ATTRIBUTE_KEY);
        if (requestHeaderContext != null && StringUtils.hasText(requestHeaderContext.sessionId())) {
            return requestHeaderContext.sessionId();
        }
        return exchange.getRequest().getHeaders().getFirst(RequestHeaderContextKeys.SESSION_ID_HEADER);
    }

}
