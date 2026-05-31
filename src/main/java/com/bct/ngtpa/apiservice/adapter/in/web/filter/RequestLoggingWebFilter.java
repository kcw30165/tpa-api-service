package com.bct.ngtpa.apiservice.adapter.in.web.filter;

import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.reactivestreams.Publisher;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Global WebFilter that:
 * <ul>
 *   <li>Resolves or generates an {@code X-Request-Id} for every inbound request.</li>
 *   <li>Stores the request ID in the HTTP response header, exchange attributes, and Reactor Context.</li>
 *   <li>Logs structured JSON events for request start, end, and error lifecycle phases.</li>
 *   <li>Optionally logs request and response bodies per endpoint-level configuration.</li>
 * </ul>
 *
 * <p>Body logging is disabled by default. Enable it globally via
 * {@code request-logging.body-logging.enabled=true} and opt in per endpoint via
 * {@code request-logging.body-logging.endpoints}.</p>
 *
 * <p><strong>Response body logging limitation:</strong> response body interception requires
 * buffering the full response payload. This is not supported for binary content types
 * (e.g. Excel exports, PDFs, octet-stream). Binary responses are passed through unmodified.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestLoggingWebFilter implements WebFilter {

    private final RequestLoggingProperties properties;
    private final LoggingSanitizer loggingSanitizer;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RequestLoggingWebFilter(RequestLoggingProperties properties,
            LoggingSanitizer loggingSanitizer,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.loggingSanitizer = loggingSanitizer;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = resolveRequestId(exchange.getRequest());
        RequestHeaderContext requestHeaderContext = resolveRequestHeaderContext(exchange.getRequest(), requestId);
        exchange.getResponse().getHeaders().set(RequestCorrelation.REQUEST_ID_HEADER, requestId);
        exchange.getAttributes().put(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY, requestId);
        exchange.getAttributes().put(RequestHeaderContextKeys.ATTRIBUTE_KEY, requestHeaderContext);

        if (!properties.isEnabled()) {
            return chain.filter(exchange)
                    .contextWrite(ctx -> withRequestContexts(ctx, requestId, requestHeaderContext));
        }

        long startNanos = System.nanoTime();
        RequestLoggingProperties.EndpointRule matchedRule = findMatchingRule(exchange.getRequest());
        boolean logRequestBody = shouldLogRequestBody(matchedRule);
        boolean logResponseBody = shouldLogResponseBody(matchedRule);
        int maxBodyBytes = effectiveMaxBodyBytes(matchedRule);

        if (logRequestBody) {
            return processWithRequestBodyLogging(
                    exchange,
                    chain,
                    requestId,
                    requestHeaderContext,
                    startNanos,
                    logResponseBody,
                    maxBodyBytes);
        }

        logRequestStart(exchange, requestId, null, maxBodyBytes);
        ServerWebExchange processExchange = logResponseBody
                ? wrapWithResponseDecorator(exchange, requestId, maxBodyBytes)
                : exchange;

        return chain.filter(processExchange)
                .doOnSuccess(v -> logRequestEnd(processExchange, requestId, startNanos))
                .doOnError(t -> logRequestError(processExchange, requestId, startNanos, t))
                .contextWrite(ctx -> withRequestContexts(ctx, requestId, requestHeaderContext));
    }

    private String resolveRequestId(ServerHttpRequest request) {
        String inbound = request.getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER);
        if (inbound != null && !inbound.isBlank()) {
            return inbound;
        }
        return UUID.randomUUID().toString();
    }

    private RequestHeaderContext resolveRequestHeaderContext(ServerHttpRequest request, String requestId) {
        return new RequestHeaderContext(
                request.getHeaders().getFirst(RequestHeaderContextKeys.ACCOUNT_REF_HEADER),
                requestId,
                request.getHeaders().getFirst(RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER));
    }

    private Mono<Void> processWithRequestBodyLogging(
            ServerWebExchange exchange,
            WebFilterChain chain,
            String requestId,
            RequestHeaderContext requestHeaderContext,
            long startNanos,
            boolean logResponseBody,
            int maxBodyBytes) {
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .defaultIfEmpty(exchange.getResponse().bufferFactory().allocateBuffer(0))
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    if (bytes.length > 0) {
                        dataBuffer.read(bytes);
                    }
                    DataBufferUtils.release(dataBuffer);

                    String bodyStr = bytes.length > 0 ? new String(bytes, StandardCharsets.UTF_8) : null;
                    logRequestStart(exchange, requestId, bodyStr, maxBodyBytes);

                    ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            return bytes.length > 0
                                    ? Flux.just(exchange.getResponse().bufferFactory().wrap(bytes))
                                    : Flux.empty();
                        }
                    };

                    ServerWebExchange processExchange = logResponseBody
                            ? exchange.mutate().request(mutatedRequest)
                                    .response(new ResponseBodyLoggingDecorator(exchange, requestId, maxBodyBytes))
                                    .build()
                            : exchange.mutate().request(mutatedRequest).build();

                    return chain.filter(processExchange)
                            .doOnSuccess(v -> logRequestEnd(processExchange, requestId, startNanos))
                            .doOnError(t -> logRequestError(processExchange, requestId, startNanos, t));
                })
                .contextWrite(ctx -> withRequestContexts(ctx, requestId, requestHeaderContext));
    }

    private ServerWebExchange wrapWithResponseDecorator(ServerWebExchange exchange, String requestId, int maxBodyBytes) {
        return exchange.mutate()
                .response(new ResponseBodyLoggingDecorator(exchange, requestId, maxBodyBytes))
                .build();
    }

    private void logRequestStart(ServerWebExchange exchange, String requestId, String body, int maxBodyBytes) {
        ServerHttpRequest req = exchange.getRequest();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", "http.request.start");
        event.put("requestId", requestId);
        event.put("method", req.getMethod().name());
        event.put("path", req.getPath().value());

        Map<String, Object> query = new LinkedHashMap<>();
        req.getQueryParams().forEach((key, values) -> query.put(key, values.size() == 1 ? values.get(0) : values));
        if (!query.isEmpty()) {
            event.put("query", loggingSanitizer.sanitizeValue(query));
        }

        if (properties.isLogHeaders()) {
            Map<String, String> headers = buildAllowedHeaders(req);
            if (!headers.isEmpty()) {
                event.put("headers", loggingSanitizer.sanitizeValue(headers));
            }
        }

        if (body != null) {
            event.put("requestBody", loggingSanitizer.sanitizeValue(truncate(body, maxBodyBytes)));
        }

        log.info("{}", toJson(event));
    }

    private void logRequestEnd(ServerWebExchange exchange, String requestId, long startNanos) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", "http.request.end");
        event.put("requestId", requestId);
        event.put("method", exchange.getRequest().getMethod().name());
        event.put("path", exchange.getRequest().getPath().value());
        event.put("status", statusCode(exchange));
        event.put("elapsedMs", elapsedMillis(startNanos));
        log.info("{}", toJson(event));
    }

    private void logRequestError(ServerWebExchange exchange, String requestId, long startNanos, Throwable throwable) {
        int status = statusCode(exchange);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", "http.request.error");
        event.put("requestId", requestId);
        event.put("method", exchange.getRequest().getMethod().name());
        event.put("path", exchange.getRequest().getPath().value());
        if (status > 0) {
            event.put("status", status);
        }
        event.put("elapsedMs", elapsedMillis(startNanos));
        event.put("exceptionType", throwable.getClass().getSimpleName());
        event.put("errorMessage", loggingSanitizer.toSafeString(throwable.getMessage()));
        log.error("{}", toJson(event));
    }

    private void logResponseBody(String requestId, String body, int maxBodyBytes) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", "http.response.body");
        event.put("requestId", requestId);
        event.put("responseBody", loggingSanitizer.sanitizeValue(truncate(body, maxBodyBytes)));
        log.info("{}", toJson(event));
    }

    private RequestLoggingProperties.EndpointRule findMatchingRule(ServerHttpRequest request) {
        String method = request.getMethod().name();
        String path = request.getPath().value();
        return properties.getBodyLogging().getEndpoints().stream()
                .filter(rule -> method.equalsIgnoreCase(rule.getMethod()))
                .filter(rule -> rule.getPathPattern() != null && pathMatcher.match(rule.getPathPattern(), path))
                .findFirst()
                .orElse(null);
    }

    private boolean shouldLogRequestBody(RequestLoggingProperties.EndpointRule rule) {
        return properties.getBodyLogging().isEnabled() && rule != null && rule.isLogRequestBody();
    }

    private boolean shouldLogResponseBody(RequestLoggingProperties.EndpointRule rule) {
        return properties.getBodyLogging().isEnabled() && rule != null && rule.isLogResponseBody();
    }

    private int effectiveMaxBodyBytes(RequestLoggingProperties.EndpointRule rule) {
        if (rule != null && rule.getMaxBodySizeBytes() != null) {
            return rule.getMaxBodySizeBytes();
        }
        return properties.getBodyLogging().getDefaultMaxBodySizeBytes();
    }

    private Context withRequestContexts(Context ctx, String requestId, RequestHeaderContext requestHeaderContext) {
        return ctx.put(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, requestId)
                .put(RequestHeaderContextKeys.CONTEXT_KEY, requestHeaderContext);
    }

    private Map<String, String> buildAllowedHeaders(ServerHttpRequest req) {
        Map<String, String> headers = new LinkedHashMap<>();
        List<String> allowlist = properties.getHeaderAllowlist();
        req.getHeaders().forEach((name, values) -> {
            if (allowlist.stream().anyMatch(name::equalsIgnoreCase)) {
                headers.put(name, String.join(", ", values));
            }
        });
        return headers;
    }

    private static int statusCode(ServerWebExchange exchange) {
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        return status != null ? status.value() : 0;
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static String truncate(String text, int maxBytes) {
        if (text == null || text.length() <= maxBytes) {
            return text;
        }
        return text.substring(0, maxBytes) + "...[truncated]";
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException exception) {
            return "{\"event\":\"log-serialization-failed\"}";
        }
    }

    private class ResponseBodyLoggingDecorator extends ServerHttpResponseDecorator {

        private final String requestId;
        private final int maxBodyBytes;

        ResponseBodyLoggingDecorator(ServerWebExchange exchange, String requestId, int maxBodyBytes) {
            super(exchange.getResponse());
            this.requestId = requestId;
            this.maxBodyBytes = maxBodyBytes;
        }

        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            MediaType contentType = getHeaders().getContentType();
            if (isBinaryContent(contentType)) {
                return super.writeWith(body);
            }
            Flux<DataBuffer> buffered = DataBufferUtils.join(Flux.from(body))
                    .flatMapMany(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        String bodyStr = new String(bytes, StandardCharsets.UTF_8);
                        logResponseBody(requestId, bodyStr, maxBodyBytes);
                        return Flux.just(bufferFactory().wrap(bytes));
                    });
            return super.writeWith(buffered);
        }

        private boolean isBinaryContent(MediaType contentType) {
            if (contentType == null) {
                return false;
            }
            return contentType.includes(MediaType.APPLICATION_OCTET_STREAM)
                    || contentType.includes(MediaType.IMAGE_PNG)
                    || contentType.includes(MediaType.IMAGE_JPEG)
                    || contentType.includes(MediaType.IMAGE_GIF)
                    || "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            .equals(contentType.toString())
                    || "application/pdf".equals(contentType.toString());
        }
    }
}
