package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.util.unit.DataSize;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

/**
 * Regression test for the live export failure: a 36-month TRPGetFundPrice response exceeded
 * Spring's 256 KB codec default. Uses a real reactor-netty server so the codec limit is exercised
 * exactly as bodyToMono(String.class) does in ApimWebClientFacade.
 */
class ApimWebClientFacadeResponseSizeLimitTest {

  private static final int BODY_BYTES = 300 * 1024;
  private static final String BODY = "x".repeat(BODY_BYTES);
  private static DisposableServer server;

  @BeforeAll
  static void startServer() {
    server =
        HttpServer.create()
            .host("127.0.0.1")
            .port(0)
            .route(
                routes ->
                    routes.post(
                        "/large",
                        (request, response) ->
                            response
                                .header("Content-Type", "text/plain")
                                .sendString(Mono.just(BODY))))
            .bindNow();
  }

  @AfterAll
  static void stopServer() {
    if (server != null) {
      server.disposeNow();
    }
  }

  private static Mono<String> fetch(WebClient.Builder builder) {
    return builder
        .baseUrl("http://127.0.0.1:" + server.port())
        .build()
        .post()
        .uri("/large")
        .retrieve()
        .bodyToMono(String.class);
  }

  private static boolean causedByBufferLimit(Throwable error) {
    for (Throwable t = error; t != null; t = t.getCause()) {
      if (t instanceof DataBufferLimitException) {
        return true;
      }
      if (t.getCause() == t) {
        return false;
      }
    }
    return false;
  }

  @Test
  void springDefaultCodecLimitRejectsLargeResponse() {
    StepVerifier.create(fetch(WebClient.builder()))
        .expectErrorMatches(ApimWebClientFacadeResponseSizeLimitTest::causedByBufferLimit)
        .verify(Duration.ofSeconds(10));
  }

  @Test
  void apimDefaultLimitAcceptsLargeResponse() {
    StepVerifier.create(
            fetch(
                WebClient.builder()
                    .codecs(ApimWebClientFacade.responseSizeLimit(new ApimProperties()))))
        .assertNext(body -> assertThat(body).hasSize(BODY_BYTES))
        .expectComplete()
        .verify(Duration.ofSeconds(10));
  }

  @Test
  void configuredLimitBelowResponseSizeStillRejects() {
    ApimProperties properties = new ApimProperties();
    properties.setMaxResponseSize(DataSize.ofKilobytes(100));

    StepVerifier.create(
            fetch(WebClient.builder().codecs(ApimWebClientFacade.responseSizeLimit(properties))))
        .expectErrorMatches(ApimWebClientFacadeResponseSizeLimitTest::causedByBufferLimit)
        .verify(Duration.ofSeconds(10));
  }
}
