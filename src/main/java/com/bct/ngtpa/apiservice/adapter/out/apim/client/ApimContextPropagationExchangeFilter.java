package com.bct.ngtpa.apiservice.adapter.out.apim.client;

import com.bct.ngtpa.apiservice.shared.web.PortalIdentityCorrelation;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

/**
 * Propagates correlation and portal identity values to APIM as outbound headers.
 *
 * <p>Reads only plain String values from the Reactor Context, never identity objects, preserving
 * the outbound adapter boundary. {@code X-Request-Id} is emitted when present; {@code X-Trust} and
 * {@code X-User-Role} are always emitted and fall back to an empty String for pre-login flows.
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

                  String trustCode =
                      ctx.getOrDefault(PortalIdentityCorrelation.TRUST_CODE_CONTEXT_KEY, null);
                  String userRole =
                      ctx.getOrDefault(PortalIdentityCorrelation.USER_ROLE_CONTEXT_KEY, null);

                  requestBuilder.header(ApimPropagationHeaders.TRUST_HEADER, text(trustCode));
                  requestBuilder.header(ApimPropagationHeaders.USER_ROLE_HEADER, text(userRole));

                  return Mono.just(requestBuilder.build());
                }));
  }

  private static String text(String value) {
    return value == null ? "" : value;
  }
}
