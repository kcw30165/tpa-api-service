package com.bct.ngtpa.apiservice.adapter.out.apim.client;

import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class ApimRequestLoggingExchangeFilter {

  private static final java.util.Set<String> FORBIDDEN_HEADERS =
      java.util.Set.of(
          "authorization",
          "cookie",
          "set-cookie",
          "session-id",
          "x-xsrf-token",
          "x-password-validation-token",
          "x-api-key",
          "api-key",
          "subscription-key",
          "x-subscription-key",
          "ocp-apim-subscription-key");

  private final RequestLoggingProperties properties;
  private final String apimCredentialHeaderName;
  private final LoggingSanitizer loggingSanitizer;
  private final ObjectMapper objectMapper;

  public ApimRequestLoggingExchangeFilter(
      RequestLoggingProperties properties,
      ApimProperties apimProperties,
      LoggingSanitizer loggingSanitizer,
      ObjectMapper objectMapper) {
    this(properties, apimProperties.getApiKeyHeaderName(), loggingSanitizer, objectMapper);
  }

  ApimRequestLoggingExchangeFilter(
      RequestLoggingProperties properties,
      String apimCredentialHeaderName,
      LoggingSanitizer loggingSanitizer,
      ObjectMapper objectMapper) {
    this.properties = properties;
    this.apimCredentialHeaderName =
        apimCredentialHeaderName == null ? "" : apimCredentialHeaderName;
    this.loggingSanitizer = loggingSanitizer;
    this.objectMapper = objectMapper;
  }

  public ExchangeFilterFunction filter() {
    return ExchangeFilterFunction.ofRequestProcessor(
        request ->
            Mono.deferContextual(
                ctx -> {
                  if (!properties.isEnabled()) {
                    return Mono.just(request);
                  }
                  String requestId =
                      ctx.getOrDefault(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, "");

                  Map<String, List<String>> headers = buildAllowedHeaders(request);

                  Map<String, Object> event = new LinkedHashMap<>();
                  event.put("event", "apim.request");
                  event.put("requestId", requestId);
                  event.put("method", request.method().name());
                  event.put("path", request.url().getPath());
                  if (properties.isLogHeaders() && !headers.isEmpty()) {
                    event.put("headers", headers);
                  }

                  log.info("{}", toJson(event));

                  return Mono.just(request);
                }));
  }

  private Map<String, List<String>> buildAllowedHeaders(
      org.springframework.web.reactive.function.client.ClientRequest request) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    if (!properties.isLogHeaders()) {
      return headers;
    }
    for (String configuredName : properties.getHeaderAllowlist()) {
      if (configuredName == null || forbidden(configuredName)) {
        continue;
      }
      request
          .headers()
          .forEach(
              (actualName, values) -> {
                if (actualName.equalsIgnoreCase(configuredName) && !values.isEmpty()) {
                  headers.put(actualName, List.copyOf(values));
                }
              });
    }
    return headers;
  }

  private boolean forbidden(String name) {
    String normalized = name.trim().toLowerCase(java.util.Locale.ROOT);
    return FORBIDDEN_HEADERS.contains(normalized)
        || (!apimCredentialHeaderName.isBlank()
            && normalized.equals(
                apimCredentialHeaderName.trim().toLowerCase(java.util.Locale.ROOT)))
        || normalized.contains("api-key")
        || normalized.contains("apikey")
        || normalized.contains("password")
        || normalized.contains("secret")
        || normalized.contains("token")
        || normalized.contains("credential")
        || normalized.contains("certificate");
  }

  private String toJson(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      return "{\"event\":\"log-serialization-failed\"}";
    }
  }
}
