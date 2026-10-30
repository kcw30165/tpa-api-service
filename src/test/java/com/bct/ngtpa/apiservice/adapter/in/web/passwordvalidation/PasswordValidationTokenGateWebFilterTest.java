package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieReader;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorResponseRenderer;
import com.bct.ngtpa.apiservice.adapter.out.passwordvalidation.JsonOperationPayloadCanonicalizer;
import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.usecase.passwordvalidation.PasswordValidationGrantService;
import com.bct.ngtpa.apiservice.shared.web.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PasswordValidationTokenGateWebFilterTest {
  private static final String REF = "acc_abcdefghijklmnopqrstuvwx";
  private static final String TOKEN = "A".repeat(43);
  private final PasswordValidationGrantService grants = mock(PasswordValidationGrantService.class);
  private final AuthenticatedSessionCookieReader cookies =
      mock(AuthenticatedSessionCookieReader.class);
  private final JsonOperationPayloadCanonicalizer bindings =
      new JsonOperationPayloadCanonicalizer();
  private final ObjectMapper json = new ObjectMapper();
  private PasswordValidationTokenGateWebFilter gate;

  @BeforeEach
  void setUp() {
    gate =
        new PasswordValidationTokenGateWebFilter(
            grants,
            cookies,
            bindings,
            ServerCodecConfigurer.create(),
            new ErrorResponseRenderer(),
            json,
            mock(FailureEventPort.class));
    when(cookies.read(any()))
        .thenReturn(Optional.of(new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx")));
    when(grants.consume(any(), any(), any(), any(), any()))
        .thenReturn(
            Mono.just(
                new PasswordValidationGrantService.ConsumeResult(
                    PasswordValidationGrantService.Status.CONSUMED, bindings.digest("{}"))));
  }

  @Test
  void explicitOrderFollowsAccountContextAndMissingContextCannotBypassGate() throws Exception {
    assertThat(gate.getClass().getAnnotation(Order.class).value())
        .isEqualTo(Ordered.HIGHEST_PRECEDENCE + 3);
    var exchange = request();
    AtomicInteger calls = new AtomicInteger();
    gate.filter(
            exchange,
            ignored -> {
              calls.incrementAndGet();
              return Mono.empty();
            })
        .block();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(
            json.readTree(exchange.getResponse().getBodyAsString().block())
                .at("/errors/0/code")
                .asText())
        .isEqualTo("err.security.authentication.required");
    assertThat(calls).hasValue(0);
    verifyNoInteractions(grants);
  }

  @Test
  void authenticatedContextWithoutCookieIsRejectedBeforeToken() {
    var exchange = request();
    authenticate(exchange);
    when(cookies.read(any())).thenReturn(Optional.empty());
    gate.filter(exchange, ignored -> Mono.error(new AssertionError("Dispatched"))).block();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(grants);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"actor-missing", "actor-id-blank", "actor-role-missing", "account-missing"})
  void incompleteAuthenticatedContextIsRejectedBeforeToken(String missingPart) {
    var exchange = request();
    PortalAccessContext context = mock(PortalAccessContext.class, RETURNS_DEEP_STUBS);
    when(context.actor().actorUserId()).thenReturn("test-actor");
    when(context.actor().actorUserRole()).thenReturn(ActorUserRole.ROLE_USER_MBR);
    when(context.account().accountRef()).thenReturn(REF);
    switch (missingPart) {
      case "actor-missing" -> when(context.actor()).thenReturn(null);
      case "actor-id-blank" -> when(context.actor().actorUserId()).thenReturn(" ");
      case "actor-role-missing" -> when(context.actor().actorUserRole()).thenReturn(null);
      case "account-missing" -> when(context.account()).thenReturn(null);
      default -> throw new AssertionError(missingPart);
    }
    exchange.getAttributes().put(PortalAccessContextKeys.ATTRIBUTE_KEY, context);

    AtomicInteger calls = new AtomicInteger();
    gate.filter(
            exchange,
            ignored -> {
              calls.incrementAndGet();
              return Mono.empty();
            })
        .block();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(calls).hasValue(0);
    verifyNoInteractions(grants);
  }

  @Test
  void consumedGrantWithoutBindingFailsClosedBeforeBodyDispatch() {
    var exchange = request();
    authenticate(exchange);
    when(grants.consume(any(), any(), any(), any(), any()))
        .thenReturn(
            Mono.just(
                new PasswordValidationGrantService.ConsumeResult(
                    PasswordValidationGrantService.Status.CONSUMED, null)));
    AtomicInteger calls = new AtomicInteger();

    gate.filter(
            exchange,
            ignored -> {
              calls.incrementAndGet();
              return Mono.empty();
            })
        .block();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(calls).hasValue(0);
    verify(grants).consume(any(), any(), any(), eq(TOKEN), eq("gate-unit"));
  }

  @Test
  void missingAccountHeaderIsRejectedBeforeToken() {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.put(PasswordValidationRouteMatcher.PERSONAL_INFORMATION_PATH)
                .header(PasswordValidationTokenGateWebFilter.TOKEN_HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}"));
    exchange.getAttributes().put(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY, "gate-unit");
    authenticate(exchange);

    gate.filter(exchange, ignored -> Mono.error(new AssertionError("Dispatched"))).block();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(grants);
  }

  @Test
  void missingContentTypeUsesUtf8ForBodyBinding() {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.put(PasswordValidationRouteMatcher.PERSONAL_INFORMATION_PATH)
                .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, REF)
                .header(PasswordValidationTokenGateWebFilter.TOKEN_HEADER, TOKEN)
                .body("{}"));
    exchange.getAttributes().put(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY, "gate-unit");
    authenticate(exchange);
    assertThat(exchange.getRequest().getHeaders().getContentType()).isNull();
    AtomicInteger calls = new AtomicInteger();

    gate.filter(
            exchange,
            actual -> {
              calls.incrementAndGet();
              assertThat(
                      actual
                          .<com.bct.ngtpa.apiservice.domain.passwordvalidation.PayloadDigest>
                              getAttribute(PasswordValidationTokenGateWebFilter.BINDING_ATTRIBUTE))
                  .isEqualTo(bindings.digest("{}"));
              return Mono.empty();
            })
        .block();

    assertThat(calls).hasValue(1);
    verify(grants).consume(any(), any(), any(), eq(TOKEN), eq("gate-unit"));
  }

  @Test
  void wrongResolvedAccountCannotAuthorizeOrConsume() {
    var exchange = request();
    authenticate(exchange);
    when(((PortalAccessContext) exchange.getAttribute(PortalAccessContextKeys.ATTRIBUTE_KEY))
            .account()
            .accountRef())
        .thenReturn("acc_012345678901234567890123");
    gate.filter(exchange, ignored -> Mono.error(new AssertionError("Dispatched"))).block();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(grants);
  }

  @ParameterizedTest
  @CsvSource({
    "GET,/api/v1/personal-information",
    "POST,/api/v1/personal-information",
    "POST,/api/v1/password-validation",
    "PUT,/api/v1/other",
    "PUT,/api/v1/personal-information/",
    "PUT,/api/v1/Personal-information"
  })
  void allOtherRoutesPassThroughExactlyOnceWithHeadersUnchanged(String method, String path) {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.method(HttpMethod.valueOf(method), path)
                .header("Fixture", "same"));
    var originalHeaders = exchange.getRequest().getHeaders();
    AtomicInteger calls = new AtomicInteger();
    gate.filter(
            exchange,
            actual -> {
              assertThat(actual).isSameAs(exchange);
              assertThat(actual.getRequest().getHeaders()).isEqualTo(originalHeaders);
              calls.incrementAndGet();
              return Mono.empty();
            })
        .block();
    assertThat(calls).hasValue(1);
    assertThat(exchange.getResponse().getHeaders().containsHeader(HttpHeaders.CACHE_CONTROL))
        .isFalse();
    verifyNoInteractions(grants, cookies);
  }

  @Test
  void bindingExistsOnlyDuringThisRequestAndDownstreamFailuresAreNotConvertedTo503() {
    var exchange = request();
    authenticate(exchange);
    var downstream = new IllegalStateException("existing downstream failure");
    StepVerifier.create(
            gate.filter(
                exchange,
                actual -> {
                  assertThat(
                          actual
                              .<com.bct.ngtpa.apiservice.domain.passwordvalidation.PayloadDigest>
                                  getAttribute(
                                      PasswordValidationTokenGateWebFilter.BINDING_ATTRIBUTE))
                      .isEqualTo(bindings.digest("{}"));
                  return Mono.error(downstream);
                }))
        .expectErrorMatches(failure -> failure == downstream)
        .verify();
    assertThat(exchange.getAttributes())
        .doesNotContainKey(PasswordValidationTokenGateWebFilter.BINDING_ATTRIBUTE);
    assertThat(exchange.getResponse().getStatusCode()).isNull();
    verify(grants).consume(any(), any(), any(), eq(TOKEN), eq("gate-unit"));
  }

  @Test
  void unavailableConsumeCannotReadTheBodyOrInvokeChain() {
    var exchange = request();
    authenticate(exchange);
    when(grants.consume(any(), any(), any(), any(), any()))
        .thenThrow(new IllegalStateException("synthetic private diagnostic"));
    gate.filter(exchange, ignored -> Mono.error(new AssertionError("Dispatched"))).block();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(exchange.getResponse().getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_JSON);
    assertThat(exchange.getResponse().getHeaders().getCacheControl()).isEqualTo("no-store");
    assertThat(exchange.getResponse().getHeaders().getFirst(RequestCorrelation.REQUEST_ID_HEADER))
        .isEqualTo("gate-unit");
    assertThat(exchange.getResponse().getBodyAsString().block())
        .contains("err.password-validation.unavailable")
        .doesNotContain("private diagnostic");
  }

  private MockServerWebExchange request() {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.put(PasswordValidationRouteMatcher.PERSONAL_INFORMATION_PATH)
                .header(RequestHeaderContextKeys.ACCOUNT_REF_HEADER, REF)
                .header(PasswordValidationTokenGateWebFilter.TOKEN_HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}"));
    exchange.getAttributes().put(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY, "gate-unit");
    return exchange;
  }

  private void authenticate(MockServerWebExchange exchange) {
    PortalAccessContext context = mock(PortalAccessContext.class, RETURNS_DEEP_STUBS);
    when(context.account().accountRef()).thenReturn(REF);
    when(context.actor().actorUserId()).thenReturn("test-actor");
    when(context.actor().actorUserRole()).thenReturn(ActorUserRole.ROLE_USER_MBR);
    exchange.getAttributes().put(PortalAccessContextKeys.ATTRIBUTE_KEY, context);
  }
}
