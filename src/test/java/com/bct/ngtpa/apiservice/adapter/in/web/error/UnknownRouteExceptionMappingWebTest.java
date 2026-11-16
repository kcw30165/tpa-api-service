package com.bct.ngtpa.apiservice.adapter.in.web.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.in.web.advice.ApiExceptionHandler;
import com.bct.ngtpa.apiservice.testcontext.UnknownRouteTestApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = UnknownRouteTestApplication.class,
    properties = {
      "api.security.require-authentication=false",
      "cors.allowed-origins[0]=http://localhost:4200"
    })
@AutoConfigureWebTestClient
class UnknownRouteExceptionMappingWebTest {

  private static final String UNKNOWN_ROUTE = "/api/v1/this-route-does-not-exist";

  @Autowired private WebTestClient webTestClient;
  @Autowired private ObjectMapper objectMapper;

  private Logger logger;
  private ListAppender<ILoggingEvent> listAppender;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(listAppender);
    listAppender.stop();
  }

  @Test
  void unknownRouteUsesClientRequestNotFoundContract() throws Exception {
    EntityExchangeResult<String> response =
        webTestClient
            .get()
            .uri(UNKNOWN_ROUTE)
            .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
            .header("X-Request-Id", "unknown-route-red")
            .exchange()
            .expectBody(String.class)
            .returnResult();
    JsonNode body = objectMapper.readTree(response.getResponseBody());

    assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatus().value());
    assertFalse(body.path("success").asBoolean());
    assertEquals("VALIDATION_FAILED", body.path("status").asText());
    assertEquals(0, body.path("messages").size());
    assertEquals(1, body.path("errors").size());
    JsonNode error = body.path("errors").get(0);
    assertEquals("FORM", error.path("type").asText());
    assertEquals("err.request.resource-not-found", error.path("code").asText());
    assertEquals("The requested resource was not found.", error.path("message").asText());
    assertEquals(0, error.path("targets").size());
    assertEquals("ERROR", error.path("severity").asText());
    assertEquals("SERVER", error.path("source").asText());
    String bodyText = response.getResponseBody();
    assertFalse(bodyText.contains(UNKNOWN_ROUTE));
    assertFalse(bodyText.contains("NoResourceFoundException"));
    assertFalse(bodyText.contains(" at "));

    assertEquals(1, listAppender.list.size());
    assertEquals(Level.WARN, listAppender.list.getFirst().getLevel());
    assertNull(listAppender.list.getFirst().getThrowableProxy());
    JsonNode logEvent = objectMapper.readTree(listAppender.list.getFirst().getFormattedMessage());
    assertEquals("http.api.error", logEvent.path("event").asText());
    assertEquals("NoResourceFoundException", logEvent.path("exceptionType").asText());
  }

  @Test
  void unknownRouteResolvesTraditionalChineseMessage() throws Exception {
    EntityExchangeResult<String> response =
        webTestClient
            .get()
            .uri(UNKNOWN_ROUTE)
            .header(HttpHeaders.ACCEPT_LANGUAGE, "zh-HK")
            .header("X-Request-Id", "unknown-route-zh-hk")
            .exchange()
            .expectBody(String.class)
            .returnResult();
    JsonNode body = objectMapper.readTree(response.getResponseBody());

    assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatus().value());
    assertEquals("找不到所要求的資源。", body.path("errors").get(0).path("message").asText());
    assertFalse(response.getResponseBody().contains(UNKNOWN_ROUTE));
  }

  @Test
  void registeredEndpointKeepsItsNormalResponse() {
    webTestClient
        .get()
        .uri("/api/v1/test/registered")
        .header("X-Request-Id", "registered-route")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
        .expectBody(String.class)
        .isEqualTo("registered");
  }

  @Test
  void unrelatedUnmappedExceptionKeepsCatchAllResponseAndErrorLog() throws Exception {
    EntityExchangeResult<String> response =
        webTestClient
            .get()
            .uri("/api/v1/test/unmapped-error")
            .header("X-Request-Id", "unmapped-error")
            .exchange()
            .expectBody(String.class)
            .returnResult();
    JsonNode body = objectMapper.readTree(response.getResponseBody());

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getStatus().value());
    assertEquals("err.system.unexpected", body.path("errors").get(0).path("code").asText());
    assertEquals(1, listAppender.list.size());
    assertEquals(Level.ERROR, listAppender.list.getFirst().getLevel());
    JsonNode logEvent = objectMapper.readTree(listAppender.list.getFirst().getFormattedMessage());
    assertEquals("IllegalStateException", logEvent.path("exceptionType").asText());
  }
}
