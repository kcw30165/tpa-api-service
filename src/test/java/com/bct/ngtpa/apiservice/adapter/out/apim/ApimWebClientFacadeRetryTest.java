package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfile;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfileResolver;
import com.bct.ngtpa.apiservice.adapter.out.apim.oauth.ApimTokenCacheEntry;
import com.bct.ngtpa.apiservice.adapter.out.apim.oauth.ApimTokenService;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ApimWebClientFacadeRetryTest {

  @Test
  void invalidTokenEvictsAndRetriesOnce() {
    AtomicInteger serverCalls = new AtomicInteger();

    ExchangeFunction ef =
        req -> {
          serverCalls.incrementAndGet();
          String auth = req.headers().getFirst("Authorization");
          if ("Bearer old-token".equals(auth)) {
            String err =
                "{\"id\":\"1\",\"error\":\"invalid_token\",\"error_description\":\"expired\"}";
            return Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST).body(err).build());
          }
          if ("Bearer new-token".equals(auth)) {
            String ok = "{\"ok\":true}";
            return Mono.just(ClientResponse.create(HttpStatus.OK).body(ok).build());
          }
          String unknown = "{\"id\":\"x\",\"error\":\"unknown\"}";
          return Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST).body(unknown).build());
        };

    WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
    ApimProperties props = new ApimProperties();

    // Token service stub that starts with old-token and refreshes to new-token
    AtomicReference<String> tokenRef = new AtomicReference<>("old-token");
    AtomicInteger refreshCount = new AtomicInteger();

    ApimTokenService tokenService =
        new ApimTokenService(builder, props) {
          @Override
          public Mono<String> getAccessToken(ApimCredentialProfile profile) {
            return Mono.just(tokenRef.get());
          }

          @Override
          public Mono<ApimTokenCacheEntry> refreshAccessToken(ApimCredentialProfile profile) {
            refreshCount.incrementAndGet();
            tokenRef.set("new-token");
            ApimTokenCacheEntry e =
                new ApimTokenCacheEntry(
                    profile.profileId(),
                    "new-token",
                    "Bearer",
                    System.currentTimeMillis() + 3600_000L,
                    null);
            return Mono.just(e);
          }
        };

    ApimCredentialProfile profile =
        new ApimCredentialProfile(
            "p1", "cid", "secret", "akey", null, "http://localhost", List.of(), null);
    ApimCredentialProfileResolver resolver =
        new ApimCredentialProfileResolver() {
          @Override
          public reactor.core.publisher.Mono<
                  com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfile>
              resolve(
                  com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope scope,
                  com.bct.ngtpa.apiservice.adapter.out.apim.credential
                          .ApimCredentialResolutionContext
                      context) {
            return reactor.core.publisher.Mono.just(profile);
          }
        };

    ApimCertificateService certificateService = new ApimCertificateService(builder, props, null);

    ApimWebClientFacade facade =
        new ApimWebClientFacade(
            WebClient.create(),
            builder,
            new ObjectMapper(),
            resolver,
            tokenService,
            certificateService,
            props);

    String resp =
        facade
            .post(
                "/api/test",
                Map.of("x", "y"),
                com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope.NON_ACCOUNT,
                new com.bct.ngtpa.apiservice.adapter.out.apim.credential
                    .ApimCredentialResolutionContext(
                    com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR, null),
                null)
            .block();

    assertEquals("{\"ok\":true}", resp);
    assertEquals(
        2, serverCalls.get(), "Server should be called twice: first failing token, then retry");
    assertEquals(1, refreshCount.get(), "Token refresh should be invoked exactly once");
  }

  @Test
  void encryptedRequestsIncludeCertificateHeader() {
    AtomicReference<String> certificateHeader = new AtomicReference<>();

    ExchangeFunction ef =
        req -> {
          certificateHeader.set(req.headers().getFirst("Certificate"));
          return Mono.just(ClientResponse.create(HttpStatus.OK).body("{\"ok\":true}").build());
        };

    WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
    ApimProperties props = new ApimProperties();
    props.getEncryption().setEnabled(true);

    ApimTokenService tokenService =
        new ApimTokenService(builder, props) {
          @Override
          public Mono<String> getAccessToken(ApimCredentialProfile profile) {
            return Mono.just("tok");
          }
        };

    ApimCredentialProfile profile =
        new ApimCredentialProfile(
            "p1", "cid", "secret", "akey", null, "http://localhost", List.of(), null);
    ApimCredentialProfileResolver resolver =
        (scope, ctx) -> reactor.core.publisher.Mono.just(profile);
    ApimCertificateService certificateService = new ApimCertificateService(builder, props, null);

    ApimWebClientFacade facade =
        new ApimWebClientFacade(
            WebClient.create(),
            builder,
            new ObjectMapper(),
            resolver,
            tokenService,
            certificateService,
            props) {
          @Override
          protected String getCertificateHeaderValue() {
            return "app-cert-header";
          }
        };

    facade
        .post(
            "/api/test",
            Map.of("x", "y"),
            com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope.NON_ACCOUNT,
            new com.bct.ngtpa.apiservice.adapter.out.apim.credential
                .ApimCredentialResolutionContext(
                com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR, null),
            null)
        .block();

    assertEquals("app-cert-header", certificateHeader.get());
  }

  @Test
  void malformedInvalidTokenBodyStillRefreshesAndRetries() {
    AtomicInteger serverCalls = new AtomicInteger();

    ExchangeFunction ef =
        req -> {
          serverCalls.incrementAndGet();
          String auth = req.headers().getFirst("Authorization");
          if ("Bearer old-token".equals(auth)) {
            String err =
                "{\n  \"id\" : \"1\",\n \"error\":\"invalid_token\"\n \"error_desciption\":\"The access token is expired or invalid\"\n}";
            return Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED).body(err).build());
          }
          return Mono.just(ClientResponse.create(HttpStatus.OK).body("{\"ok\":true}").build());
        };

    WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
    ApimProperties props = new ApimProperties();

    AtomicReference<String> tokenRef = new AtomicReference<>("old-token");
    AtomicInteger refreshCount = new AtomicInteger();

    ApimTokenService tokenService =
        new ApimTokenService(builder, props) {
          @Override
          public Mono<String> getAccessToken(ApimCredentialProfile profile) {
            return Mono.just(tokenRef.get());
          }

          @Override
          public Mono<ApimTokenCacheEntry> refreshAccessToken(ApimCredentialProfile profile) {
            refreshCount.incrementAndGet();
            tokenRef.set("new-token");
            return Mono.just(
                new ApimTokenCacheEntry(
                    profile.profileId(),
                    "new-token",
                    "Bearer",
                    System.currentTimeMillis() + 3600_000L,
                    null));
          }
        };

    ApimCredentialProfile profile =
        new ApimCredentialProfile(
            "p1", "cid", "secret", "akey", null, "http://localhost", List.of(), null);
    ApimCredentialProfileResolver resolver =
        (scope, ctx) -> reactor.core.publisher.Mono.just(profile);
    ApimCertificateService certificateService = new ApimCertificateService(builder, props, null);

    ApimWebClientFacade facade =
        new ApimWebClientFacade(
            WebClient.create(),
            builder,
            new ObjectMapper(),
            resolver,
            tokenService,
            certificateService,
            props);

    String resp =
        facade
            .post(
                "/api/test",
                Map.of("x", "y"),
                com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope.NON_ACCOUNT,
                new com.bct.ngtpa.apiservice.adapter.out.apim.credential
                    .ApimCredentialResolutionContext(
                    com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR, null),
                null)
            .block();

    assertEquals("{\"ok\":true}", resp);
    assertEquals(2, serverCalls.get());
    assertEquals(1, refreshCount.get());
  }

  @Test
  void invalidPublicKeyBodyOnlyPathEvictsCertificateWithoutRetryingStalePayload() {
    AtomicInteger serverCalls = new AtomicInteger();
    AtomicInteger evictions = new AtomicInteger();

    ExchangeFunction ef =
        req -> {
          serverCalls.incrementAndGet();
          return Mono.just(
              ClientResponse.create(HttpStatus.BAD_REQUEST)
                  .body("{\"error\":\"invalid_public_key\"}")
                  .build());
        };

    WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
    ApimProperties props = new ApimProperties();
    ApimTokenService tokenService = fixedTokenService(builder, props, "tok");
    ApimCredentialProfile profile =
        new ApimCredentialProfile(
            "p1",
            "client",
            "secret",
            "api-key",
            "https://token.test",
            "https://apim.test",
            java.util.List.of(),
            "/certificate");
    ApimCredentialProfileResolver resolver = captureResolver(profile);
    ApimCertificateService certificateService =
        trackingCertificateService(builder, props, evictions);

    ApimWebClientFacade facade =
        new ApimWebClientFacade(
            WebClient.create(),
            builder,
            new ObjectMapper(),
            resolver,
            tokenService,
            certificateService,
            props);

    StepVerifier.create(
            facade.post(
                "/api/test",
                Map.of("x", "y"),
                com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope.NON_ACCOUNT,
                new com.bct.ngtpa.apiservice.adapter.out.apim.credential
                    .ApimCredentialResolutionContext(
                    com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR, null),
                null))
        .expectErrorSatisfies(
            ex -> {
              assertEquals(ApimException.class, ex.getClass());
              assertEquals("APIM request failed with status=400", ex.getMessage());
            })
        .verify();

    assertEquals(1, serverCalls.get());
    assertEquals(1, evictions.get());
  }

  @Test
  void repeatedInvalidPublicKeyOnBodyOnlyPathPropagatesAfterFirstResponseWithoutRetry() {
    AtomicInteger serverCalls = new AtomicInteger();
    AtomicInteger evictions = new AtomicInteger();

    ExchangeFunction ef =
        req -> {
          serverCalls.incrementAndGet();
          return Mono.just(
              ClientResponse.create(HttpStatus.BAD_REQUEST)
                  .body("{\"error\":\"invalid_public_key\"}")
                  .build());
        };

    WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
    ApimProperties props = new ApimProperties();
    ApimCredentialProfile profile =
        new ApimCredentialProfile(
            "p1",
            "client",
            "secret",
            "api-key",
            "https://token.test",
            "https://apim.test",
            java.util.List.of(),
            "/certificate");
    ApimWebClientFacade facade =
        new ApimWebClientFacade(
            WebClient.create(),
            builder,
            new ObjectMapper(),
            captureResolver(profile),
            fixedTokenService(builder, props, "tok"),
            trackingCertificateService(builder, props, evictions),
            props);

    StepVerifier.create(
            facade.post(
                "/api/test",
                Map.of("x", "y"),
                com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope.NON_ACCOUNT,
                new com.bct.ngtpa.apiservice.adapter.out.apim.credential
                    .ApimCredentialResolutionContext(
                    com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR, null),
                null))
        .expectErrorSatisfies(
            ex -> {
              assertEquals(ApimException.class, ex.getClass());
              assertEquals("APIM request failed with status=400", ex.getMessage());
            })
        .verify();

    assertEquals(1, serverCalls.get());
    assertEquals(1, evictions.get());
  }

  @Test
  void nonRetryableErrorsPropagateImmediately() {
    AtomicInteger serverCalls = new AtomicInteger();
    AtomicInteger refreshCount = new AtomicInteger();

    ExchangeFunction ef =
        req -> {
          serverCalls.incrementAndGet();
          return Mono.just(ClientResponse.create(HttpStatus.BAD_GATEWAY).body("not-json").build());
        };

    WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
    ApimProperties props = new ApimProperties();
    ApimTokenService tokenService =
        new ApimTokenService(builder, props) {
          @Override
          public Mono<String> getAccessToken(ApimCredentialProfile profile) {
            return Mono.just("tok");
          }

          @Override
          public Mono<ApimTokenCacheEntry> refreshAccessToken(ApimCredentialProfile profile) {
            refreshCount.incrementAndGet();
            return Mono.error(new IllegalStateException("should not refresh"));
          }
        };

    ApimCredentialProfile profile =
        new ApimCredentialProfile(
            "p1", "cid", "secret", "akey", null, "http://localhost", List.of(), null);
    ApimWebClientFacade facade =
        new ApimWebClientFacade(
            WebClient.create(),
            builder,
            new ObjectMapper(),
            captureResolver(profile),
            tokenService,
            trackingCertificateService(builder, props, new AtomicInteger()),
            props);

    StepVerifier.create(
            facade.post(
                "/api/test",
                Map.of("x", "y"),
                com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope.NON_ACCOUNT,
                new com.bct.ngtpa.apiservice.adapter.out.apim.credential
                    .ApimCredentialResolutionContext(
                    com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR, null),
                null))
        .expectErrorSatisfies(
            ex -> {
              assertEquals(ApimException.class, ex.getClass());
              assertEquals("APIM request failed with status=502", ex.getMessage());
            })
        .verify();

    assertEquals(1, serverCalls.get());
    assertEquals(0, refreshCount.get());
  }

  @Test
  void transportFailureExcludesRequestAndResponsePayloadsFromLogsAndException() throws Exception {
    String requestMarker = "O17-REQUEST-DOB-NESTED-CIPHERTEXT-UNKNOWN";
    String responseMarker = "O17-RESPONSE-PLAINTEXT-NESTED-CIPHERTEXT-NONJSON";
    AtomicInteger serverCalls = new AtomicInteger();
    AtomicReference<String> transmittedBody = new AtomicReference<>();
    ExchangeFunction exchange =
        request -> {
          serverCalls.incrementAndGet();
          transmittedBody.set(serializeRequestBody(request));
          return Mono.just(
              ClientResponse.create(HttpStatus.BAD_GATEWAY).body(responseMarker).build());
        };
    WebClient.Builder builder = WebClient.builder().exchangeFunction(exchange);
    ApimProperties properties = new ApimProperties();
    ApimCredentialProfile profile =
        new ApimCredentialProfile(
            "p1", "client", "secret", "key", null, "http://localhost", List.of(), null);
    ApimWebClientFacade facade =
        new ApimWebClientFacade(
            WebClient.create(),
            builder,
            new ObjectMapper(),
            captureResolver(profile),
            fixedTokenService(builder, properties, "token"),
            trackingCertificateService(builder, properties, new AtomicInteger()),
            properties);
    ch.qos.logback.classic.Logger logger =
        (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(ApimWebClientFacade.class);
    ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
        new ch.qos.logback.core.read.ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      StepVerifier.create(
              facade.post(
                  "/api/privacy",
                  Map.of(
                      "ordinary", "visible-only-on-wire",
                      "dob", requestMarker,
                      "nested", Map.of("identity", requestMarker),
                      "ciphertext", requestMarker,
                      "unknown", requestMarker),
                  com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope
                      .NON_ACCOUNT,
                  new com.bct.ngtpa.apiservice.adapter.out.apim.credential
                      .ApimCredentialResolutionContext(
                      com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR, null),
                  null))
          .expectErrorSatisfies(
              error -> {
                assertEquals(ApimException.class, error.getClass());
                org.assertj.core.api.Assertions.assertThat(error.getMessage())
                    .isEqualTo("APIM request failed with status=502")
                    .doesNotContain(responseMarker, requestMarker);
                org.assertj.core.api.Assertions.assertThat(error.getCause()).isNull();
              })
          .verify();
      assertEquals(1, serverCalls.get());
      org.assertj.core.api.Assertions.assertThat(new ObjectMapper().readTree(transmittedBody.get()))
          .isEqualTo(
              new ObjectMapper()
                  .valueToTree(
                      Map.of(
                          "ordinary", "visible-only-on-wire",
                          "dob", requestMarker,
                          "nested", Map.of("identity", requestMarker),
                          "ciphertext", requestMarker,
                          "unknown", requestMarker)));
      org.assertj.core.api.Assertions.assertThat(appender.list)
          .allSatisfy(
              event -> {
                org.assertj.core.api.Assertions.assertThat(event.getFormattedMessage())
                    .doesNotContain(requestMarker, responseMarker, "visible-only-on-wire");
                if (event.getThrowableProxy() != null) {
                  org.assertj.core.api.Assertions.assertThat(event.getThrowableProxy().getMessage())
                      .doesNotContain(requestMarker, responseMarker);
                }
              });
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void usesConfiguredBaseUrlAndSkipsBlankCertificateHeader() {
    AtomicReference<URI> requestUrl = new AtomicReference<>();
    AtomicReference<String> apiKeyHeader = new AtomicReference<>();
    AtomicReference<String> certificateHeader = new AtomicReference<>();

    ExchangeFunction ef =
        req -> {
          requestUrl.set(req.url());
          apiKeyHeader.set(req.headers().getFirst("X-Api-Key"));
          certificateHeader.set(req.headers().getFirst("Certificate"));
          return Mono.just(ClientResponse.create(HttpStatus.OK).body("{\"ok\":true}").build());
        };

    WebClient.Builder builder = WebClient.builder().exchangeFunction(ef);
    ApimProperties props = new ApimProperties();
    props.setBaseUrl("http://configured.example");
    props.setApiKeyHeaderName("X-Api-Key");
    props.getEncryption().setEnabled(true);

    ApimCredentialProfile profile =
        new ApimCredentialProfile(
            "p1", "cid", "secret", "tok", null, "http://configured.example", List.of(), null);
    ApimWebClientFacade facade =
        new ApimWebClientFacade(
            WebClient.create(),
            builder,
            new ObjectMapper(),
            captureResolver(profile),
            fixedTokenService(builder, props, "tok"),
            trackingCertificateService(builder, props, new AtomicInteger()),
            props) {
          @Override
          protected String getCertificateHeaderValue() {
            return "  ";
          }
        };

    assertEquals(
        "{\"ok\":true}",
        facade
            .post(
                "/api/test",
                Map.of("x", "y"),
                com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope.NON_ACCOUNT,
                new com.bct.ngtpa.apiservice.adapter.out.apim.credential
                    .ApimCredentialResolutionContext(
                    com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR, null),
                null)
            .block());
    assertEquals("http://configured.example/api/test", requestUrl.get().toString());
    assertEquals("tok", apiKeyHeader.get());
    assertEquals(null, certificateHeader.get());
  }

  private static String serializeRequestBody(
      org.springframework.web.reactive.function.client.ClientRequest request) {
    MockClientHttpRequest mockRequest = new MockClientHttpRequest(request.method(), request.url());
    request
        .body()
        .insert(
            mockRequest,
            new BodyInserter.Context() {
              @Override
              public List<HttpMessageWriter<?>> messageWriters() {
                return ExchangeStrategies.withDefaults().messageWriters();
              }

              @Override
              public java.util.Optional<ServerHttpRequest> serverRequest() {
                return java.util.Optional.empty();
              }

              @Override
              public Map<String, Object> hints() {
                return Map.of();
              }
            })
        .block();
    return mockRequest.getBodyAsString().block();
  }

  private static ApimTokenService fixedTokenService(
      WebClient.Builder builder, ApimProperties props, String token) {
    return new ApimTokenService(builder, props) {
      @Override
      public Mono<String> getAccessToken(ApimCredentialProfile profile) {
        return Mono.just(token);
      }
    };
  }

  private static ApimCertificateService trackingCertificateService(
      WebClient.Builder builder, ApimProperties props, AtomicInteger evictions) {
    return new ApimCertificateService(builder, props, null) {
      @Override
      public void evictCertificate(String profileId) {
        evictions.incrementAndGet();
      }
    };
  }

  private static ApimCredentialProfileResolver captureResolver(ApimCredentialProfile profile) {
    return (scope, context) -> {
      // operationName is no longer part of ApimCredentialResolutionContext in strict routing
      // just assert context is present and return the profile
      org.junit.jupiter.api.Assertions.assertNotNull(context);
      return Mono.just(profile);
    };
  }
}
