package com.bct.ngtpa.apiservice.adapter.in.web.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdateNotificationsReadStatusRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.bct.ngtpa.apiservice.shared.web.PortalAccessContextKeys;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;

/**
 * Sub-task 1.8.1 (M1 closure, step 1 of 3). Unit tests for the newly-extracted {@link
 * PublicMessageResolver}, covering every branch {@code ApiExceptionHandler.resolvePublicMessage}
 * and its private helpers have today: pre-login suppression, the per-entry {@link MessagePolicy},
 * Accept-Language resolution, the portal-context account/trust/scheme fields, the reflective {@code
 * lang} lookup on a bound request target (both the direct-method and getter-method paths), and the
 * {@code null}/blank trimming rules.
 */
class PublicMessageResolverTest {

  private static final ErrorMessageResolver RESOLVER =
      (errorCode, locale, accountEnv, trustCode, schemeType) ->
          "resolved:"
              + errorCode
              + ":"
              + locale
              + ":"
              + accountEnv
              + ":"
              + trustCode
              + ":"
              + schemeType;

  private final PublicMessageResolver resolver = new PublicMessageResolver(RESOLVER);

  @Test
  void constructorRejectsNullErrorMessageResolver() {
    assertThrows(NullPointerException.class, () -> new PublicMessageResolver(null));
  }

  @Test
  void resolveRejectsNullArguments() {
    var descriptor = singleResolveDescriptor();
    var exception = new IllegalStateException("boom");
    var exchange = exchange("/api/v1/fund-price", null, null);

    assertThrows(NullPointerException.class, () -> resolver.resolve(null, exception, exchange));
    assertThrows(NullPointerException.class, () -> resolver.resolve(descriptor, null, exchange));
    assertThrows(NullPointerException.class, () -> resolver.resolve(descriptor, exception, null));
  }

  @Test
  void noneEntryAlwaysResolvesToNullEvenOutsidePreLogin() {
    var descriptor = singleNoneDescriptor();
    var exchange = exchange("/api/v1/account/summary", null, null);

    List<String> messages = resolver.resolve(descriptor, new IllegalStateException("x"), exchange);

    assertEquals(1, messages.size());
    assertNull(messages.getFirst());
  }

  @Test
  void resolveEntryOutsidePreLoginResolvesUsingAcceptLanguageAndNoPortalContext() {
    var descriptor = singleResolveDescriptor();
    var exchange = exchange("/api/v1/fund-price", "en-US", null);

    List<String> messages = resolver.resolve(descriptor, new IllegalStateException("x"), exchange);

    assertEquals(1, messages.size());
    assertEquals("resolved:err.code:en-US:null:null:null", messages.getFirst());
  }

  @Test
  void resolveEntryUsesPortalContextAccountEnvTrustCodeAndSchemeTypeWhenPresent() {
    var descriptor = singleResolveDescriptor();
    var exchange =
        exchange("/api/v1/fund-price", "zh-HK", portalAccessContext("JP ", " TRUST", "SCHEME"));

    List<String> messages = resolver.resolve(descriptor, new IllegalStateException("x"), exchange);

    assertEquals("resolved:err.code:zh-HK:JP:TRUST:SCHEME", messages.getFirst());
  }

  @Test
  void blankAccountFieldsTrimToNullRatherThanBlank() {
    var descriptor = singleResolveDescriptor();
    var exchange = exchange("/api/v1/fund-price", null, portalAccessContext("  ", "", null));

    List<String> messages = resolver.resolve(descriptor, new IllegalStateException("x"), exchange);

    assertEquals("resolved:err.code:null:null:null:null", messages.getFirst());
  }

  @Test
  void preLoginPathSuppressesResolveEntryEvenWithPortalContextAndAcceptLanguagePresent() {
    var descriptor = singleResolveDescriptor();
    var exchange =
        exchange("/api/v1/auth/login", "en-US", portalAccessContext("JP", "TRUST", "SCHEME"));

    List<String> messages = resolver.resolve(descriptor, new IllegalStateException("x"), exchange);

    assertNull(messages.getFirst());
  }

  @Test
  void everyPreLoginPrefixSuppressesTheResolvedMessage() {
    var descriptor = singleResolveDescriptor();
    for (String path :
        List.of(
            "/api/v1/auth/registration",
            "/api/v1/auth/captcha",
            "/api/v1/auth/login",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/username-recovery")) {
      var exchange = exchange(path, "en-US", portalAccessContext("JP", "TRUST", "SCHEME"));
      assertNull(resolver.resolve(descriptor, new IllegalStateException("x"), exchange).getFirst());
    }
  }

  @Test
  void bindExceptionLangFieldIsReadReflectivelyWhenAcceptLanguageAbsent() throws Exception {
    var descriptor = singleResolveDescriptor();
    var exchange = exchange("/api/v1/fund-price", null, null);
    var bindException = bindExceptionWithLangField(new RequestWithLangField("fr-FR"));

    List<String> messages = resolver.resolve(descriptor, bindException, exchange);

    assertEquals("resolved:err.code:fr-FR:null:null:null", messages.getFirst());
  }

  @Test
  void bindExceptionLangGetterIsUsedWhenDirectMethodDoesNotExist() throws Exception {
    var descriptor = singleResolveDescriptor();
    var exchange = exchange("/api/v1/fund-price", null, null);
    var bindException = bindExceptionWithLangField(new RequestWithLangGetter("de-DE"));

    List<String> messages = resolver.resolve(descriptor, bindException, exchange);

    assertEquals("resolved:err.code:de-DE:null:null:null", messages.getFirst());
  }

  @Test
  void acceptLanguageTakesPrecedenceOverTheReflectiveLangLookup() throws Exception {
    var descriptor = singleResolveDescriptor();
    var exchange = exchange("/api/v1/fund-price", "ja-JP", null);
    var bindException = bindExceptionWithLangField(new RequestWithLangField("fr-FR"));

    List<String> messages = resolver.resolve(descriptor, bindException, exchange);

    assertEquals("resolved:err.code:ja-JP:null:null:null", messages.getFirst());
  }

  @Test
  void nonBindExceptionNeverTriggersTheReflectiveLangLookup() {
    var descriptor = singleResolveDescriptor();
    var exchange = exchange("/api/v1/fund-price", null, null);

    List<String> messages = resolver.resolve(descriptor, new IllegalStateException("x"), exchange);

    assertEquals("resolved:err.code:null:null:null:null", messages.getFirst());
  }

  @Test
  void multiEntryDescriptorResolvesEachEntryIndependentlyInOrder() {
    var descriptor =
        new ErrorDescriptor(
            HttpStatus.BAD_REQUEST,
            ApiStatus.VALIDATION_FAILED,
            List.of(
                new ErrorEntry("FIELD", "err.a", List.of("a"), MessagePolicy.RESOLVE),
                new ErrorEntry("FIELD", "err.b", List.of("b"), MessagePolicy.NONE),
                new ErrorEntry("FIELD", "err.c", List.of("c"), MessagePolicy.RESOLVE)),
            "http.api.error",
            LogLevel.WARN,
            false,
            java.util.Map.of());
    var exchange = exchange("/api/v1/fund-price", "en-US", null);

    List<String> messages = resolver.resolve(descriptor, new IllegalStateException("x"), exchange);

    assertEquals(3, messages.size());
    assertEquals("resolved:err.a:en-US:null:null:null", messages.get(0));
    assertNull(messages.get(1));
    assertEquals("resolved:err.c:en-US:null:null:null", messages.get(2));
  }

  // ───────────────────────── fixtures ─────────────────────────

  private static ErrorDescriptor singleResolveDescriptor() {
    return ErrorDescriptor.single(
        HttpStatus.BAD_REQUEST,
        ApiStatus.VALIDATION_FAILED,
        "FORM",
        "err.code",
        List.of(),
        "http.api.error",
        LogLevel.WARN,
        false);
  }

  private static ErrorDescriptor singleNoneDescriptor() {
    return ErrorDescriptor.singleWithoutMessage(
        HttpStatus.SERVICE_UNAVAILABLE,
        ApiStatus.SYSTEM_ERROR,
        "SYSTEM",
        "err.code",
        List.of(),
        "http.api.error",
        LogLevel.ERROR,
        false);
  }

  private static PortalAccessContext portalAccessContext(
      String accountEnv, String trustCode, String schemeType) {
    return new PortalAccessContext(
        new ActorContext("U", ActorUserRole.ROLE_USER_MBR),
        new AccountContext(
            "ACC-1", accountEnv, "POL-1", "CERT-1", trustCode, schemeType, TermStatus.BLANK, null));
  }

  private static ServerWebExchange exchange(
      String path, String acceptLanguage, PortalAccessContext portalAccessContext) {
    var builder = MockServerHttpRequest.get(path);
    if (acceptLanguage != null) {
      builder = builder.header("Accept-Language", acceptLanguage);
    }
    MockServerWebExchange exchange = MockServerWebExchange.from(builder);
    if (portalAccessContext != null) {
      exchange.getAttributes().put(PortalAccessContextKeys.ATTRIBUTE_KEY, portalAccessContext);
    }
    return exchange;
  }

  private static WebExchangeBindException bindExceptionWithLangField(Object target)
      throws Exception {
    Method method =
        PublicMessageResolverTest.class.getDeclaredMethod(
            "notificationPatch", UpdateNotificationsReadStatusRequest.class);
    MethodParameter parameter = new MethodParameter(method, 0);
    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "request");
    return new WebExchangeBindException(parameter, bindingResult);
  }

  @SuppressWarnings("unused")
  private void notificationPatch(@RequestBody UpdateNotificationsReadStatusRequest request) {
    // Method signature used only to construct MethodParameter for WebExchangeBindException.
  }

  /** A bind target exposing a direct {@code lang()} method, matching record-style accessors. */
  private record RequestWithLangField(String lang) {}

  /** A bind target exposing only a conventional {@code getLang()} getter, no direct method. */
  private static final class RequestWithLangGetter {
    private final String lang;

    private RequestWithLangGetter(String lang) {
      this.lang = lang;
    }

    public String getLang() {
      return lang;
    }
  }
}
