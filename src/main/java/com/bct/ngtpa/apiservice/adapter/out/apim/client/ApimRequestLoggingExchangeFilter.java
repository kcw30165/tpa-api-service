package com.bct.ngtpa.apiservice.adapter.out.apim.client;

import com.bct.ngtpa.apiservice.config.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.config.logging.RequestLoggingWebFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ApimRequestLoggingExchangeFilter {

    private static final Logger logger = LoggerFactory.getLogger(ApimRequestLoggingExchangeFilter.class);

    private final LoggingSanitizer loggingSanitizer;
    private final ObjectMapper objectMapper;

    public ExchangeFilterFunction filter() {
        return ExchangeFilterFunction.ofRequestProcessor(request ->
                Mono.deferContextual(ctx -> {
                    String requestId = ctx.getOrDefault(
                            RequestLoggingWebFilter.REQUEST_ID_CONTEXT_KEY,
                            ""
                    );

                    Map<String, List<String>> headers = new LinkedHashMap<>();
                    request.headers().forEach(headers::put);

                    Map<String, Object> event = new LinkedHashMap<>();
                    event.put("event", "apim.request");
                    event.put("requestId", requestId);
                    event.put("method", request.method().name());
                    event.put("url", String.valueOf(request.url()));
                    event.put("headers", loggingSanitizer.sanitizeValue(headers));

                    logger.info("{}", toJson(event));

                    return Mono.just(request);
                })
        );
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{\"event\":\"log-serialization-failed\"}";
        }
    }
}
