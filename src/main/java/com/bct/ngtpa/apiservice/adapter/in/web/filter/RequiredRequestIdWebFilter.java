package com.bct.ngtpa.apiservice.adapter.in.web.filter;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Enforces the client-supplied X-Request-Id contract for application APIs. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequiredRequestIdWebFilter implements WebFilter {

  private static final String API_PREFIX = "/api/";
  private static final String TARGET = RequestCorrelation.REQUEST_ID_HEADER;
  private final ObjectMapper objectMapper;

  public RequiredRequestIdWebFilter(ObjectMapper objectMapper) {
    this.objectMapper =
        java.util.Objects.requireNonNull(objectMapper, "objectMapper must not be null");
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String path = exchange.getRequest().getPath().value();
    if (path == null || !path.startsWith(API_PREFIX)) {
      return chain.filter(exchange);
    }

    String inbound =
        exchange.getRequest().getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER);
    if (inbound != null && !inbound.isBlank()) {
      return chain.filter(exchange);
    }

    return reject(exchange);
  }

  private Mono<Void> reject(ServerWebExchange exchange) {
    String code = ErrorCodes.REQUEST_ID_MISSING;
    MutationResponse<Void> response =
        MutationResponse.failure(
            ApiStatus.VALIDATION_FAILED,
            List.of(ApiError.field(code, null, List.of(TARGET), "SERVER")));

    byte[] bytes;
    try {
      bytes = objectMapper.writeValueAsBytes(response);
    } catch (JsonProcessingException exception) {
      return Mono.error(exception);
    }

    exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
    return exchange.getResponse().writeWith(Mono.just(buffer));
  }
}
