package com.bct.ngtpa.apiservice.adapter.out.apim.client;

import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

@Component
public class ApimRequestIdExchangeFilter {

    public ExchangeFilterFunction filter() {
        return ExchangeFilterFunction.ofRequestProcessor(request ->
                Mono.deferContextual(ctx -> {
                    String requestId = ctx.getOrDefault(
                            RequestCorrelation.REQUEST_ID_CONTEXT_KEY,
                            null
                    );

                    ClientRequest.Builder requestBuilder = ClientRequest.from(request);

                    if (requestId != null && !requestId.isBlank()) {
                        requestBuilder.header(RequestCorrelation.REQUEST_ID_HEADER, requestId);
                    }

                    return Mono.just(requestBuilder.build());
                })
        );
    }
}
