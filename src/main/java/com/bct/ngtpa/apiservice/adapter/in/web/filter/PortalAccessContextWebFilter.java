package com.bct.ngtpa.apiservice.adapter.in.web.filter;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieWriter;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.*;
import com.bct.ngtpa.apiservice.shared.web.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

/** Resolves selected-account context from portal_sid, Account-Ref, and authenticated CSRF. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public final class PortalAccessContextWebFilter implements WebFilter {
    private final PortalAccessContextPort portalAccessContextPort;
    private final ValidateAuthenticatedSessionUseCase sessionValidation;
    private final AuthenticatedSessionCookieWriter sessionCookies;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String accountRef = accountRef(exchange);
        if (!StringUtils.hasText(accountRef)) return chain.filter(exchange);
        String sessionId = cookie(exchange, sessionCookies.cookieName());
        String csrf = exchange.getRequest().getHeaders().getFirst(RequestHeaderContextKeys.XSRF_TOKEN_HEADER);
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(csrf)) {
            return Mono.error(new AuthenticatedPortalContextResolutionException());
        }
        AuthenticatedSessionId id;
        try { id = new AuthenticatedSessionId(sessionId.trim()); }
        catch (RuntimeException failure) {
            return Mono.error(new AuthenticatedPortalContextResolutionException());
        }
        String requestId = exchange.getRequest().getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER);
        return sessionValidation.validate(new ValidateAuthenticatedSessionCommand(id, csrf, requestId))
                .then(portalAccessContextPort.resolvePortalAccessContext(accountRef, sessionId))
                .flatMap(context -> continueWith(exchange, chain, context));
    }

    private Mono<Void> continueWith(ServerWebExchange exchange, WebFilterChain chain, PortalAccessContext context) {
        exchange.getAttributes().put(PortalAccessContextKeys.ATTRIBUTE_KEY, context);
        return chain.filter(exchange).contextWrite(c -> c.put(PortalAccessContextKeys.CONTEXT_KEY, context));
    }
    private String accountRef(ServerWebExchange exchange) {
        RequestHeaderContext context = (RequestHeaderContext) exchange.getAttributes()
                .get(RequestHeaderContextKeys.ATTRIBUTE_KEY);
        if (context != null && StringUtils.hasText(context.accountRef())) return context.accountRef();
        return exchange.getRequest().getHeaders().getFirst(RequestHeaderContextKeys.ACCOUNT_REF_HEADER);
    }
    private String cookie(ServerWebExchange exchange, String name) {
        var cookie = exchange.getRequest().getCookies().getFirst(name);
        return cookie == null ? null : cookie.getValue();
    }
}
