package com.bct.ngtpa.apiservice.adapter.out.apim.client;

import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.shared.web.PortalAccessContextKeys;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

/**
 * Propagates request correlation and authenticated portal-access identity to APIM. Emits {@code
 * X-Request-Id} when present, and always emits {@code X-Trust} and {@code X-User-Role}, using an
 * empty string when no authenticated {@link PortalAccessContext} is present (pre-login flows).
 */
@Component
public class ApimContextPropagationExchangeFilter {

  public ExchangeFilterFunction filter() {
    return ExchangeFilterFunction.ofRequestProcessor(
        request ->
            Mono.deferContextual(
                ctx -> {
                  ClientRequest.Builder requestBuilder = ClientRequest.from(request);

                  String requestId =
                      ctx.getOrDefault(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, null);
                  if (requestId != null && !requestId.isBlank()) {
                    requestBuilder.header(RequestCorrelation.REQUEST_ID_HEADER, requestId);
                  }

                  PortalAccessContext portalAccessContext = portalAccessContext(ctx);
                  requestBuilder.header(
                      ApimPropagationHeaders.TRUST_HEADER, trust(portalAccessContext));
                  requestBuilder.header(
                      ApimPropagationHeaders.USER_ROLE_HEADER, userRole(portalAccessContext));

                  return Mono.just(requestBuilder.build());
                }));
  }

  private static String trust(PortalAccessContext context) {
    if (context == null || context.account() == null || context.account().trustCode() == null) {
      return "";
    }
    return context.account().trustCode();
  }

  private static String userRole(PortalAccessContext context) {
    if (context == null || context.actor() == null || context.actor().actorUserRole() == null) {
      return "";
    }
    return context.actor().actorUserRole().toString();
  }

  private static PortalAccessContext portalAccessContext(ContextView ctx) {
    if (!ctx.hasKey(PortalAccessContextKeys.CONTEXT_KEY)) {
      return null;
    }
    Object value = ctx.get(PortalAccessContextKeys.CONTEXT_KEY);
    return value instanceof PortalAccessContext portalAccessContext ? portalAccessContext : null;
  }
}
