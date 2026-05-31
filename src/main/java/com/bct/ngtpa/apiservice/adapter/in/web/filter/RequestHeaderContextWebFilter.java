package com.bct.ngtpa.apiservice.adapter.in.web.filter;

import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

public class RequestHeaderContextWebFilter implements WebFilter {

    private final RequestLoggingWebFilter requestLoggingWebFilter;

    public RequestHeaderContextWebFilter(RequestLoggingWebFilter requestLoggingWebFilter) {
        this.requestLoggingWebFilter = requestLoggingWebFilter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return requestLoggingWebFilter.filter(exchange, chain);
    }
}