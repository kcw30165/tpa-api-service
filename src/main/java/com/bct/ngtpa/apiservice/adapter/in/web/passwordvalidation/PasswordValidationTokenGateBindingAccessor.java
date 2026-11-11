package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import com.bct.ngtpa.apiservice.domain.passwordvalidation.PayloadDigest;
import java.util.Optional;
import org.springframework.web.server.ServerWebExchange;

public final class PasswordValidationTokenGateBindingAccessor {

  public Optional<PayloadDigest> binding(ServerWebExchange exchange) {
    Object value =
        exchange.getAttributes().get(PasswordValidationTokenGateWebFilter.BINDING_ATTRIBUTE);
    return value instanceof PayloadDigest digest ? Optional.of(digest) : Optional.empty();
  }
}
