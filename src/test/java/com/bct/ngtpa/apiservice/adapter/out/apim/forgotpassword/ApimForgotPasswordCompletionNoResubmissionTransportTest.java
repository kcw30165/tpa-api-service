package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimAppCertificateService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimCertificateService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimPayloadCryptoService;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimWebClientFacade;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.DefaultApimCredentialProfileResolver;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimAesKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimPayloadFieldTransformer;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimRsaPayloadCrypto;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.DefaultApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.oauth.ApimTokenCacheEntry;
import com.bct.ngtpa.apiservice.adapter.out.apim.oauth.ApimTokenService;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompletionCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompletionResult;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

/** Reset-password transport tests exercise the real APIM facade against a local HTTP server. */
class ApimForgotPasswordCompletionNoResubmissionTransportTest {
  private static final String PATH = "/ws/NGTPA/v1/TRPResetPassword";
  private static final String CUSTOMER_ID = "test-member-123";
  private static final String PASSWORD = "TestOnly!123";
  private static final String SUCCESS_BODY =
      """
      {"response":{"err-message":"","data":[{"success":true,"cust-id":"test-member-123",
      "update-date":"29/07/2026","update-time":"14:30:15"}]}}
      """;

  @Test
  void http503DoesNotResubmitReset() throws Exception {
    assertFailedOnce("503", false);
  }

  @Test
  void connectionFailureDoesNotResubmitReset() throws Exception {
    assertFailedOnce("connection", false);
  }

  @Test
  void timeoutDoesNotResubmitReset() throws Exception {
    assertFailedOnce("timeout", false);
  }

  @Test
  void invalidTokenDoesNotResubmitResetOrRefreshToken() throws Exception {
    try (var harness = new Harness("invalid_token", false)) {
      expectApimFailure(harness);
      assertThat(harness.resetPosts).hasValue(1);
      verify(harness.tokens, never()).refreshAccessToken(any());
      verify(harness.tokens, never()).evictToken(anyString());
    }
  }

  @Test
  void invalidPublicKeyDoesNotResubmitResetOrRefreshCertificate() throws Exception {
    try (var harness = new Harness("invalid_public_key", true)) {
      expectApimFailure(harness);
      assertThat(harness.resetPosts).hasValue(1);
      verify(harness.certificates, never()).refreshPublicKey(any());
      verify(harness.certificates, never()).evictCertificate(anyString());
    }
  }

  @Test
  void confirmedSuccessAndRecognizedBusinessRejectionRemainUnchanged() throws Exception {
    try (var success = new Harness("success", false)) {
      StepVerifier.create(success.adapter.complete(command()))
          .assertNext(
              result -> {
                assertThat(result.status()).isEqualTo(ForgotPasswordCompletionResult.Status.RESET);
                assertThat(result.updateDate()).isEqualTo("29/07/2026");
                assertThat(result.updateTime()).isEqualTo("14:30:15");
              })
          .verifyComplete();
      assertThat(success.resetPosts).hasValue(1);
    }

    try (var rejected = new Harness("business_rejection", false)) {
      StepVerifier.create(rejected.adapter.complete(command()))
          .assertNext(
              result -> {
                assertThat(result.status())
                    .isEqualTo(ForgotPasswordCompletionResult.Status.REJECTED);
                assertThat(result.downstreamErrorCode()).isEqualTo("err.tpa.password.invalid");
              })
          .verifyComplete();
      assertThat(rejected.resetPosts).hasValue(1);
    }
  }

  private void assertFailedOnce(String scenario, boolean encrypted) throws Exception {
    try (var harness = new Harness(scenario, encrypted)) {
      expectApimFailure(harness);
      assertThat(harness.resetPosts).hasValue(1);
    }
  }

  private void expectApimFailure(Harness harness) {
    StepVerifier.create(harness.adapter.complete(command()))
        .expectError(ApimException.class)
        .verify(Duration.ofSeconds(5));
  }

  private static ForgotPasswordCompletionCommand command() {
    return new ForgotPasswordCompletionCommand(
        CUSTOMER_ID, PASSWORD, "test-request-id", ActorUserRole.ROLE_USER_MBR);
  }

  private static final class Harness implements AutoCloseable {
    private final AtomicInteger resetPosts = new AtomicInteger();
    private final ApimTokenService tokens = mock(ApimTokenService.class);
    private final ApimCertificateService certificates = mock(ApimCertificateService.class);
    private final ApimForgotPasswordCompletionAdapter adapter;
    private final DisposableServer server;

    private Harness(String scenario, boolean encrypted) throws Exception {
      server =
          HttpServer.create()
              .host("127.0.0.1")
              .port(0)
              .handle(
                  (request, response) ->
                      request
                          .receive()
                          .aggregate()
                          .asString()
                          .flatMap(
                              body -> {
                                assertThat(request.uri()).isEqualTo(PATH);
                                assertThat(body).isNotBlank();
                                resetPosts.incrementAndGet();
                                return switch (scenario) {
                                  case "503" ->
                                      response
                                          .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                                          .sendString(Mono.just("unavailable"))
                                          .then();
                                  case "connection" -> {
                                    response.withConnection(Disposable::dispose);
                                    yield Mono.empty();
                                  }
                                  case "timeout" -> Mono.never();
                                  case "invalid_token" ->
                                      response
                                          .status(HttpStatus.UNAUTHORIZED.value())
                                          .sendString(Mono.just("{\"error\":\"invalid_token\"}"))
                                          .then();
                                  case "invalid_public_key" ->
                                      response
                                          .status(HttpStatus.UNAUTHORIZED.value())
                                          .sendString(
                                              Mono.just("{\"error\":\"invalid_public_key\"}"))
                                          .then();
                                  case "business_rejection" ->
                                      response
                                          .sendString(
                                              Mono.just(
                                                  "{\"response\":{\"err-message\":\"err.tpa.password.invalid\",\"data\":[]}}"))
                                          .then();
                                  default -> response.sendString(Mono.just(SUCCESS_BODY)).then();
                                };
                              }))
              .bindNow();

      KeyPair keyPair = encrypted ? generateKeyPair() : null;
      var properties = new ApimProperties();
      properties.setTimeoutMilliseconds(scenario.equals("timeout") ? 100 : 5000);
      properties.getEncryption().setEnabled(encrypted);
      properties.getEncryption().setRequestFields(List.of("cust-id", "password"));
      properties.getRetry().getBusinessCall().setMaxAttempts(3);
      properties.getRetry().getBusinessCall().setBackoffMilliseconds(0);
      properties.getRouting().setNonAccountProfileId("reset");
      var profile = new ApimProperties.CredentialProfiles.Profile();
      profile.setProfileId("reset");
      profile.setBaseUrl("http://127.0.0.1:" + server.port());
      profile.setApiKey("test-api-key");
      profile.setCertificatePath("/certificate");
      properties.getCredentialProfiles().setProfiles(List.of(profile));

      when(tokens.getAccessToken(any())).thenReturn(Mono.just("test-token"));
      when(tokens.refreshAccessToken(any()))
          .thenReturn(
              Mono.just(new ApimTokenCacheEntry("reset", "refreshed-token", "Bearer", 0L, null)));

      if (encrypted) {
        when(certificates.getPublicKey(any())).thenReturn(Mono.just(keyPair.getPublic()));
        when(certificates.refreshPublicKey(any())).thenReturn(Mono.just(keyPair.getPublic()));
      }

      var rsa = mock(ApimRsaPayloadCrypto.class);
      var appCertificate = mock(ApimAppCertificateService.class);
      if (encrypted) {
        when(appCertificate.getAppPrivateKey()).thenReturn(keyPair.getPrivate());
        when(rsa.createSignedJwt(anyString(), ArgumentMatchers.any()))
            .thenReturn("test-signed-payload");
        when(rsa.encryptPlaintext(anyString(), eq(keyPair.getPublic())))
            .thenReturn("test-encrypted-payload");
      }
      var crypto =
          new ApimPayloadCryptoService(
              properties,
              new ObjectMapper(),
              new ApimPayloadFieldTransformer(),
              rsa,
              new ApimAesKeyFactory(),
              appCertificate);
      var facade =
          new ApimWebClientFacade(
              WebClient.create(),
              WebClient.builder(),
              new ObjectMapper(),
              new DefaultApimCredentialProfileResolver(properties),
              tokens,
              certificates,
              properties);
      adapter =
          new ApimForgotPasswordCompletionAdapter(
              new DefaultApimExchangeExecutor(facade, crypto, properties));
    }

    @Override
    public void close() {
      server.disposeNow();
    }

    private static KeyPair generateKeyPair() throws Exception {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    }
  }
}
