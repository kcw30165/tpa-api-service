package com.bct.ngtpa.apiservice.adapter.out.apim.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingProperties;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

class ApimRequestLoggingExchangeFilterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final LoggingSanitizer loggingSanitizer = loggingSanitizerWithDefaults();
  private final RequestLoggingProperties requestLoggingProperties = enabledHeaderProperties();
  private final ApimRequestLoggingExchangeFilter filter =
      new ApimRequestLoggingExchangeFilter(requestLoggingProperties, "", objectMapper);

  private static RequestLoggingProperties enabledHeaderProperties() {
    RequestLoggingProperties properties = new RequestLoggingProperties();
    properties.setEnabled(true);
    properties.setLogHeaders(true);
    properties.setHeaderAllowlist(java.util.List.of("User-Agent", "Accept", "Content-Type"));
    return properties;
  }

  private static LoggingSanitizer loggingSanitizerWithDefaults() {
    MaskingProperties props = new MaskingProperties();
    props
        .getLogging()
        .setSensitiveTokens(
            java.util.List.of(
                "authorization",
                "token",
                "secret",
                "password",
                "apiKey",
                "Certificate",
                "clientSecret"));
    return new LoggingSanitizer(new ObjectMapper(), new MaskingService(props));
  }

  private ListAppender<ILoggingEvent> listAppender;
  private Logger filterLogger;

  @BeforeEach
  void setUpLogCapture() {
    filterLogger = (Logger) LoggerFactory.getLogger(ApimRequestLoggingExchangeFilter.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    filterLogger.addAppender(listAppender);
  }

  @AfterEach
  void tearDownLogCapture() {
    filterLogger.detachAppender(listAppender);
  }

  @Test
  void logEventIsValidJson() throws Exception {
    ExchangeFilterFunction exchangeFilter = filter.filter();
    ClientRequest request =
        ClientRequest.create(HttpMethod.POST, URI.create("https://api.example.test/notifications"))
            .header("Accept", "application/json")
            .build();

    invokeFilter(exchangeFilter, request, "req-001");

    assertNotNull(listAppender.list);
    assertEquals(1, listAppender.list.size());
    String message = listAppender.list.get(0).getFormattedMessage();
    objectMapper.readTree(message); // valid JSON - no exception thrown
  }

  @Test
  void logEventNameIsApimRequest() throws Exception {
    ExchangeFilterFunction exchangeFilter = filter.filter();
    ClientRequest request =
        ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.test/test")).build();

    invokeFilter(exchangeFilter, request, "req-002");

    String message = listAppender.list.get(0).getFormattedMessage();
    var node = objectMapper.readTree(message);
    assertEquals("apim.request", node.get("event").asText());
  }

  @Test
  void logEventContainsMethodPathRequestIdAndAllowlistedHeadersOnly() throws Exception {
    ExchangeFilterFunction exchangeFilter = filter.filter();
    ClientRequest request =
        ClientRequest.create(
                HttpMethod.POST,
                URI.create("https://api.example.test/contributions?identity=O17-QUERY-MARKER"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer O17-AUTH-MARKER")
            .header("Cookie", "sid=O17-COOKIE-MARKER")
            .header("X-API-Key", "O17-API-KEY-MARKER")
            .build();

    invokeFilter(exchangeFilter, request, "req-xyz");

    String message = listAppender.list.get(0).getFormattedMessage();
    var node = objectMapper.readTree(message);
    assertEquals("POST", node.get("method").asText());
    assertEquals("/contributions", node.get("path").asText());
    assertEquals("req-xyz", node.get("requestId").asText());
    org.junit.jupiter.api.Assertions.assertNull(node.get("url"));
    assertEquals("application/json", node.get("headers").get("Content-Type").get(0).asText());
    assertEquals("application/json", node.get("headers").get("Accept").get(0).asText());
    assertEquals(2, node.get("headers").size());
    assertTrue(!message.contains("O17-QUERY-MARKER"));
    assertTrue(!message.contains("O17-AUTH-MARKER"));
    assertTrue(!message.contains("O17-COOKIE-MARKER"));
    assertTrue(!message.contains("O17-API-KEY-MARKER"));
  }

  @Test
  void sensitiveHeadersAreMaskedInLogOutput() throws Exception {
    ExchangeFilterFunction exchangeFilter = filter.filter();
    ClientRequest request =
        ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.test/test"))
            .header("Authorization", "Bearer super-secret-token")
            .header("Certificate", "encoded-cert-data")
            .header("clientSecret", "my-secret-value")
            .build();

    invokeFilter(exchangeFilter, request, "req-003");

    String message = listAppender.list.get(0).getFormattedMessage();
    assertTrue(message.contains("apim.request"), "event name should be present");
    assertTrue(!message.contains("super-secret-token"), "Authorization value must be masked");
    assertTrue(!message.contains("encoded-cert-data"), "Certificate value must be masked");
    assertTrue(!message.contains("my-secret-value"), "clientSecret value must be masked");
  }

  @Test
  void loggingDisabledPassesRequestThroughWithoutEvent() {
    RequestLoggingProperties properties = enabledHeaderProperties();
    properties.setEnabled(false);
    ApimRequestLoggingExchangeFilter disabled =
        new ApimRequestLoggingExchangeFilter(properties, "", objectMapper);
    invokeFilter(
        disabled.filter(),
        ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.test/test")).build(),
        "req-disabled");
    assertEquals(0, listAppender.list.size());
  }

  @Test
  void headersDisabledOmitsHeadersField() throws Exception {
    RequestLoggingProperties properties = enabledHeaderProperties();
    properties.setLogHeaders(false);
    ApimRequestLoggingExchangeFilter withoutHeaders =
        new ApimRequestLoggingExchangeFilter(properties, "", objectMapper);
    invokeFilter(
        withoutHeaders.filter(),
        ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.test/test"))
            .header("Accept", "application/json")
            .build(),
        "req-no-headers");
    var node = objectMapper.readTree(listAppender.list.get(0).getFormattedMessage());
    org.junit.jupiter.api.Assertions.assertNull(node.get("headers"));
  }

  @Test
  void configuredAllowlistIsCaseInsensitiveAndSensitiveHeadersFailClosed() throws Exception {
    RequestLoggingProperties properties = enabledHeaderProperties();
    properties.setHeaderAllowlist(
        java.util.List.of("user-agent", "ACCEPT", "Authorization", "X-Password-Validation-Token"));
    ApimRequestLoggingExchangeFilter configured =
        new ApimRequestLoggingExchangeFilter(properties, "", objectMapper);
    invokeFilter(
        configured.filter(),
        ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.test/test"))
            .header("User-Agent", "O17-AGENT")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer O17-AUTH")
            .header("X-Password-Validation-Token", "O17-PASSWORD-TOKEN")
            .build(),
        "req-configured");
    String message = listAppender.list.get(0).getFormattedMessage();
    var headers = objectMapper.readTree(message).get("headers");
    assertEquals("O17-AGENT", headers.get("User-Agent").get(0).asText());
    assertEquals("application/json", headers.get("Accept").get(0).asText());
    assertEquals(2, headers.size());
    assertTrue(!message.contains("O17-AUTH"));
    assertTrue(!message.contains("O17-PASSWORD-TOKEN"));
  }

  @Test
  void configuredCredentialAndSensitiveHeaderVariantsFailClosedWhileRequestIsUnchanged()
      throws Exception {
    RequestLoggingProperties properties = enabledHeaderProperties();
    properties.setHeaderAllowlist(
        java.util.List.of(
            "Accept",
            "X-Custom-Apim-Credential",
            "x-custom-apim-credential",
            "X-Subscription-Key",
            "Authorization",
            "Cookie",
            "Set-Cookie",
            "Session-Id",
            "X-XSRF-TOKEN",
            "X-Password-Validation-Token",
            "X-Client-Secret",
            "X-Access-Token",
            "X-Credential-Name",
            "X-Client-Certificate",
            "X-Password-Key",
            "X-API-Key-Variant"));
    ApimRequestLoggingExchangeFilter configured =
        new ApimRequestLoggingExchangeFilter(properties, "X-Custom-Apim-Credential", objectMapper);
    ClientRequest request =
        ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.test/test"))
            .header("Accept", "application/json")
            .header("x-CUSTOM-apim-CREDENTIAL", "O17-CUSTOM-CREDENTIAL")
            .header("X-Subscription-Key", "O17-SUBSCRIPTION")
            .header("Authorization", "O17-AUTH")
            .header("Cookie", "O17-COOKIE")
            .header("Set-Cookie", "O17-SET-COOKIE")
            .header("Session-Id", "O17-SESSION")
            .header("X-XSRF-TOKEN", "O17-XSRF")
            .header("X-Password-Validation-Token", "O17-PASSWORD-VALIDATION")
            .header("X-Client-Secret", "O17-SECRET")
            .header("X-Access-Token", "O17-TOKEN")
            .header("X-Credential-Name", "O17-CREDENTIAL")
            .header("X-Client-Certificate", "O17-CERTIFICATE")
            .header("X-Password-Key", "O17-PASSWORD")
            .header("X-API-Key-Variant", "O17-API-KEY")
            .build();
    AtomicReference<ClientRequest> captured = new AtomicReference<>();
    configured
        .filter()
        .filter(
            request,
            forwarded -> {
              captured.set(forwarded);
              return Mono.just(ClientResponse.create(HttpStatus.OK).build());
            })
        .contextWrite(ctx -> ctx.put(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, "req-sensitive"))
        .block();
    String message = listAppender.list.get(0).getFormattedMessage();
    var headers = objectMapper.readTree(message).get("headers");
    assertEquals("application/json", headers.get("Accept").get(0).asText());
    assertEquals(1, headers.size());
    assertTrue(!message.contains("O17-"));
    assertEquals(request.headers(), captured.get().headers());
  }

  @Test
  void requestIsPassedThroughUnmodified() {
    ExchangeFilterFunction exchangeFilter = filter.filter();
    ClientRequest request =
        ClientRequest.create(HttpMethod.GET, URI.create("https://api.example.test/test"))
            .header("Accept", "application/json")
            .build();
    AtomicReference<ClientRequest> captured = new AtomicReference<>();

    exchangeFilter
        .filter(
            request,
            req -> {
              captured.set(req);
              return Mono.just(ClientResponse.create(HttpStatus.OK).build());
            })
        .block();

    assertNotNull(captured.get());
    assertEquals(request.url(), captured.get().url());
    assertEquals(request.method(), captured.get().method());
  }

  private void invokeFilter(
      ExchangeFilterFunction exchangeFilter, ClientRequest request, String requestId) {
    exchangeFilter
        .filter(request, req -> Mono.just(ClientResponse.create(HttpStatus.OK).build()))
        .contextWrite(ctx -> ctx.put(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, requestId))
        .block();
  }
}
