package com.bct.ngtpa.apiservice.adapter.in.web.support;

import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextProvider;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.PortalAccessContextKeys;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Reactor Context-backed implementation of CurrentPortalAccessContextProvider.
 *
 * <p>It only reads the PortalAccessContext already stored by PortalAccessContextWebFilter.
 * It does not call PortalAccessContextPort and does not resolve by Account-Ref.</p>
 */
@Component
public class ReactorCurrentPortalAccessContextProvider implements CurrentPortalAccessContextProvider {

    static final String MISSING_CONTEXT_MESSAGE =
            "PortalAccessContext is not available in the current request context.";
    static final String INVALID_CONTEXT_TYPE_MESSAGE =
            "PortalAccessContext value in the current request context is invalid.";

    @Override
    public Mono<PortalAccessContext> current() {
        return currentOrEmpty()
                .switchIfEmpty(Mono.error(new PortalAccessContextResolutionException(
                        ErrorCodes.MEMBER_CONTEXT_INVALID,
                        MISSING_CONTEXT_MESSAGE)));
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
                    INVALID_CONTEXT_TYPE_MESSAGE));
        });
    }
}
