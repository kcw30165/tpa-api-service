package com.bct.ngtpa.apiservice.adapter.in.web.support;

import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextResolver;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.PortalAccessContextKeys;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ReactorPortalAccessContextResolver implements PortalAccessContextResolver {

    @Override
    public Mono<PortalAccessContext> current() {
        return currentOrEmpty()
                .switchIfEmpty(Mono.error(new PortalAccessContextResolutionException(
                        ErrorCodes.MEMBER_CONTEXT_INVALID,
                        "PortalAccessContext is not available in the current request context.")));
    }

    @Override
    public Mono<PortalAccessContext> currentOrEmpty() {
        return Mono.deferContextual(contextView -> {
            if (!contextView.hasKey(PortalAccessContextKeys.CONTEXT_KEY)) {
                return Mono.empty();
            }
            Object value = contextView.get(PortalAccessContextKeys.CONTEXT_KEY);
            if (value instanceof PortalAccessContext portalAccessContext) {
                return Mono.just(portalAccessContext);
            }
            return Mono.error(new PortalAccessContextResolutionException(
                    ErrorCodes.MEMBER_CONTEXT_INVALID,
                    "PortalAccessContext value in the current request context is invalid."));
        });
    }
}
