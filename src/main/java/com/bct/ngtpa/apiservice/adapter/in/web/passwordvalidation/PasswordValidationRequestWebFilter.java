package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedCsrfValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

/** Route-local response policy, including failures from the earlier account-context filter. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public final class PasswordValidationRequestWebFilter implements WebFilter {
  private final PasswordValidationWebMapper mapper;
  private final ObjectMapper json;

  public PasswordValidationRequestWebFilter(PasswordValidationWebMapper mapper, ObjectMapper json) {
    this.mapper = mapper;
    this.json = json;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    if (!PasswordValidationRouteMatcher.matches(exchange.getRequest())) {
      return chain.filter(exchange);
    }
    exchange.getResponse().getHeaders().setCacheControl("no-store");
    return chain
        .filter(exchange)
        .onErrorResume(
            AuthenticatedCsrfValidationException.class,
            failure -> {
              var response = mapper.csrfInvalid();
              exchange.getResponse().setStatusCode(response.getStatusCode());
              exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
              try {
                return exchange
                    .getResponse()
                    .writeWith(
                        Mono.just(
                            exchange
                                .getResponse()
                                .bufferFactory()
                                .wrap(json.writeValueAsBytes(response.getBody()))));
              } catch (JsonProcessingException ignored) {
                return exchange.getResponse().setComplete();
              }
            });
  }
}
