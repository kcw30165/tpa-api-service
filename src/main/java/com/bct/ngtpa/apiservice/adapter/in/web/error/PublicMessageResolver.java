package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.bct.ngtpa.apiservice.shared.web.PortalAccessContextKeys;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;

/**
 * Resolves the public-facing message(s) for a resolved {@link ErrorDescriptor}: pre-login path
 * check, {@code Accept-Language} locale, portal-context account/trust/scheme fields, and a
 * reflective {@code lang} lookup on a bound request target.
 *
 * <p>{@link #resolve(ErrorDescriptor, Throwable, ServerWebExchange)} returns one message per {@link
 * ErrorEntry} in {@link ErrorDescriptor#errors()}, in the same order: {@code null} for {@link
 * MessagePolicy#NONE} entries and the resolved message for {@link MessagePolicy#RESOLVE} entries.
 */
@Component
public final class PublicMessageResolver {

  private final ErrorMessageResolver errorMessageResolver;

  public PublicMessageResolver(ErrorMessageResolver errorMessageResolver) {
    this.errorMessageResolver =
        Objects.requireNonNull(errorMessageResolver, "errorMessageResolver");
  }

  public List<String> resolve(
      ErrorDescriptor descriptor, Throwable exception, ServerWebExchange exchange) {
    Objects.requireNonNull(descriptor, "descriptor");
    Objects.requireNonNull(exception, "exception");
    Objects.requireNonNull(exchange, "exchange");
    return descriptor.errors().stream()
        .map(
            entry ->
                entry.policy() == MessagePolicy.RESOLVE
                    ? resolvePublicMessage(entry.code(), exchange, exception)
                    : null)
        .toList();
  }

  private String resolvePublicMessage(
      String errorCode, ServerWebExchange exchange, Throwable exception) {
    if (isPreLoginAuthRequest(exchange)) {
      return null;
    }
    ErrorMessageContext context = resolveErrorMessageContext(exchange, exception);
    return errorMessageResolver.resolve(
        errorCode,
        context.locale(),
        context.accountEnv(),
        context.trustCode(),
        context.schemeType());
  }

  private ErrorMessageContext resolveErrorMessageContext(
      ServerWebExchange exchange, Throwable exception) {
    PortalAccessContext portalAccessContext = resolvePortalAccessContext(exchange);
    return new ErrorMessageContext(
        firstNonBlank(requestLocale(exchange), contextValue(exception, "lang")),
        accountEnv(portalAccessContext),
        trustCode(portalAccessContext),
        schemeType(portalAccessContext));
  }

  private PortalAccessContext resolvePortalAccessContext(ServerWebExchange exchange) {
    if (isPreLoginAuthRequest(exchange)) {
      return null;
    }
    Object attribute = exchange.getAttributes().get(PortalAccessContextKeys.ATTRIBUTE_KEY);
    return attribute instanceof PortalAccessContext portalAccessContext
        ? portalAccessContext
        : null;
  }

  private boolean isPreLoginAuthRequest(ServerWebExchange exchange) {
    if (exchange == null) {
      return false;
    }
    String path = exchange.getRequest().getPath().value();
    return path.startsWith("/api/v1/auth/registration")
        || path.startsWith("/api/v1/auth/captcha")
        || path.startsWith("/api/v1/auth/login")
        || path.startsWith("/api/v1/auth/forgot-password")
        || path.startsWith("/api/v1/auth/username-recovery");
  }

  private String accountEnv(PortalAccessContext context) {
    return context == null || context.account() == null
        ? null
        : trimToNull(context.account().accountEnv());
  }

  private String trustCode(PortalAccessContext context) {
    return context == null || context.account() == null
        ? null
        : trimToNull(context.account().trustCode());
  }

  private String schemeType(PortalAccessContext context) {
    return context == null || context.account() == null
        ? null
        : trimToNull(context.account().schemeType());
  }

  private String requestLocale(ServerWebExchange exchange) {
    try {
      return exchange.getRequest().getHeaders().getAcceptLanguageAsLocales().stream()
          .findFirst()
          .map(Locale::toLanguageTag)
          .orElse(null);
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }

  private String contextValue(Throwable exception, String propertyName) {
    if (!(exception instanceof WebExchangeBindException bindException)) {
      return null;
    }
    Object target = bindException.getTarget();
    if (target == null) {
      return null;
    }
    try {
      return trimToNull(target.getClass().getMethod(propertyName).invoke(target));
    } catch (ReflectiveOperationException ignored) {
      return getterContextValue(target, propertyName);
    }
  }

  private String getterContextValue(Object target, String propertyName) {
    String getterName =
        "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    try {
      return trimToNull(target.getClass().getMethod(getterName).invoke(target));
    } catch (ReflectiveOperationException ignored) {
      return null;
    }
  }

  private String firstNonBlank(String first, String second) {
    return StringUtils.hasText(first) ? first : second;
  }

  private String trimToNull(Object value) {
    if (value == null) {
      return null;
    }
    String text = value.toString().trim();
    return StringUtils.hasText(text) ? text : null;
  }

  private record ErrorMessageContext(
      String locale, String accountEnv, String trustCode, String schemeType) {}
}
