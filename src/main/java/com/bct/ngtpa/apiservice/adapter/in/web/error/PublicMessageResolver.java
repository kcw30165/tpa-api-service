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
 * Sub-task 1.8.1 (M1 closure, step 1 of 3): extracts {@code
 * ApiExceptionHandler.resolvePublicMessage(...)} and every private helper it calls
 * (resolveErrorMessageContext, resolvePortalAccessContext, isPreLoginAuthRequest,
 * accountEnv/trustCode/schemeType, requestLocale, contextValue, getterContextValue, firstNonBlank,
 * trimToNull, and the ErrorMessageContext record) into this standalone class, verbatim -- same
 * logic, same branches, same reflective lookups, same pre-login path list.
 *
 * <p>This class is NOT yet wired into {@code ApiExceptionHandler} or {@code
 * ExceptionMappingRegistryConfig} in this sub-task. {@code ApiExceptionHandler} keeps its own copy
 * of this logic unchanged for now, so none of the ~22 files that construct {@code
 * ApiExceptionHandler} or call its {@code handleXxx(...)} methods need to change in this step.
 * Sub-task 1.8.3 removes the duplicated private methods from {@code ApiExceptionHandler}, adds this
 * class as a constructor dependency, and updates every caller at the same time A1's method collapse
 * happens.
 *
 * <p>{@link #resolve(ErrorDescriptor, Throwable, ServerWebExchange)} is the new entry point this
 * class adds on top of the extracted logic: given a resolved {@link ErrorDescriptor} (one or more
 * {@link ErrorEntry} values, sub-task 1.7), it returns one message per entry, in the same order,
 * {@code null} for {@link MessagePolicy#NONE} entries and the resolved message for {@link
 * MessagePolicy#RESOLVE} entries -- reproducing exactly what {@code
 * ApiExceptionHandler.delegate(...)} currently does inline per {@link ErrorEntry}.
 *
 * <p>One intentional omission versus {@code ApiExceptionHandler}'s current private methods: the
 * 3-argument {@code firstNonBlank(first, second, third)} overload is not carried over here, since
 * it is confirmed dead code in {@code ApiExceptionHandler} today (never called from any of its
 * methods). Flagged here rather than silently dropped; if this is incorrect, restoring it is a
 * one-method addition.
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
    if (exchange == null || exchange.getRequest() == null) {
      return false;
    }
    String path = exchange.getRequest().getPath().value();
    return path != null
        && (path.startsWith("/api/v1/auth/registration")
            || path.startsWith("/api/v1/auth/captcha")
            || path.startsWith("/api/v1/auth/login")
            || path.startsWith("/api/v1/auth/forgot-password")
            || path.startsWith("/api/v1/auth/username-recovery"));
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
