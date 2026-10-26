package com.bct.ngtpa.apiservice.infrastructure.logging;

import com.bct.ngtpa.apiservice.shared.web.PortalIdentityCorrelation;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import io.micrometer.context.ContextRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Hooks;

/** Propagates existing request and portal identity Reactor Context values into MDC. */
@Configuration(proxyBeanMethods = false)
public class ReactorMdcPropagationConfig {

  static final String MDC_REQUEST_ID = "requestId";
  static final String MDC_TRUST_CODE = "trustCode";
  static final String MDC_USER_ROLE = "userRole";

  @PostConstruct
  void enable() {
    ContextRegistry registry = ContextRegistry.getInstance();
    register(registry, RequestCorrelation.REQUEST_ID_CONTEXT_KEY, MDC_REQUEST_ID);
    register(registry, PortalIdentityCorrelation.TRUST_CODE_CONTEXT_KEY, MDC_TRUST_CODE);
    register(registry, PortalIdentityCorrelation.USER_ROLE_CONTEXT_KEY, MDC_USER_ROLE);
    Hooks.enableAutomaticContextPropagation();
  }

  @PreDestroy
  void disable() {
    Hooks.disableAutomaticContextPropagation();
    ContextRegistry registry = ContextRegistry.getInstance();
    registry.removeThreadLocalAccessor(RequestCorrelation.REQUEST_ID_CONTEXT_KEY);
    registry.removeThreadLocalAccessor(PortalIdentityCorrelation.TRUST_CODE_CONTEXT_KEY);
    registry.removeThreadLocalAccessor(PortalIdentityCorrelation.USER_ROLE_CONTEXT_KEY);
    MDC.remove(MDC_REQUEST_ID);
    MDC.remove(MDC_TRUST_CODE);
    MDC.remove(MDC_USER_ROLE);
  }

  private static void register(ContextRegistry registry, String contextKey, String mdcKey) {
    registry.removeThreadLocalAccessor(contextKey);
    registry.registerThreadLocalAccessor(
        contextKey,
        () -> MDC.get(mdcKey),
        value -> putOrRemove(mdcKey, value),
        () -> MDC.remove(mdcKey));
  }

  private static void putOrRemove(String key, String value) {
    if (StringUtils.hasText(value)) {
      MDC.put(key, value);
    } else {
      MDC.remove(key);
    }
  }
}
