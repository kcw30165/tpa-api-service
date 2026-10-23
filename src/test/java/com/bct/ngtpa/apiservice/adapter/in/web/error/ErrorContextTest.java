package com.bct.ngtpa.apiservice.adapter.in.web.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class ErrorContextTest {

  @Test
  void fromExtractsMethodPathAndRequestIdWhenAttributePresent() {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/fund-price"));
    exchange.getAttributes().put(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY, "req-1");

    ErrorContext context = ErrorContext.from(exchange);

    assertEquals("GET", context.httpMethod());
    assertEquals("/api/v1/fund-price", context.path());
    assertEquals("req-1", context.requestId());
    assertTrue(context.hasRequestId());
  }

  @Test
  void fromLeavesRequestIdNullWhenAttributeAbsent() {
    var exchange = MockServerWebExchange.from(MockServerHttpRequest.patch("/api/v1/notifications"));

    ErrorContext context = ErrorContext.from(exchange);

    assertEquals("PATCH", context.httpMethod());
    assertEquals("/api/v1/notifications", context.path());
    assertNull(context.requestId());
    assertFalse(context.hasRequestId());
  }

  @Test
  void hasRequestIdIsFalseForBlankRequestId() {
    var context = new ErrorContext("GET", "/api/v1/fund-price", "   ");
    assertFalse(context.hasRequestId());
  }

  @Test
  void fromRejectsNullExchange() {
    assertThrows(NullPointerException.class, () -> ErrorContext.from(null));
  }

  @Test
  void canonicalConstructorRejectsNullHttpMethod() {
    assertThrows(NullPointerException.class, () -> new ErrorContext(null, "/path", "req-1"));
  }

  @Test
  void canonicalConstructorRejectsNullPath() {
    assertThrows(NullPointerException.class, () -> new ErrorContext("GET", null, "req-1"));
  }

  @Test
  void canonicalConstructorAllowsNullRequestId() {
    var context = new ErrorContext("GET", "/path", null);
    assertNull(context.requestId());
    assertFalse(context.hasRequestId());
  }
}
