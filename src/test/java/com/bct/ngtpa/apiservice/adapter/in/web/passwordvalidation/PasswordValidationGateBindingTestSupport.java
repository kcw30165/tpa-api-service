package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import com.bct.ngtpa.apiservice.domain.passwordvalidation.PayloadDigest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;

public final class PasswordValidationGateBindingTestSupport {
  private static final PayloadDigest BINDING = new PayloadDigest(1, "a".repeat(64));

  private PasswordValidationGateBindingTestSupport() {}

  public static void bind(ServerWebExchange exchange) {
    exchange.getAttributes().put(PasswordValidationTokenGateWebFilter.BINDING_ATTRIBUTE, BINDING);
  }

  public static WebFilter bindingFilter() {
    return (exchange, chain) -> {
      bind(exchange);
      return chain.filter(exchange);
    };
  }

  public static PayloadDigest binding() {
    return BINDING;
  }
}
