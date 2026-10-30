package com.bct.ngtpa.apiservice.adapter.out.apim.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.out.apim.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimAesKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimPayloadFieldTransformer;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimRsaPayloadCrypto;
import com.bct.ngtpa.apiservice.adapter.out.apim.oauth.ApimTokenService;
import com.bct.ngtpa.apiservice.adapter.out.apim.passwordvalidation.ApimPasswordValidationAdapter;
import com.bct.ngtpa.apiservice.adapter.out.apim.passwordvalidation.PasswordValidationApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.passwordvalidation.dto.TrpPwdValidationDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.passwordvalidation.dto.TrpPwdValidationRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.*;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServer;

/** Transport tests deliberately use the real executor/facade, not a feature-transport double. */
class ApimNoResubmissionTransportTest {
  private static final String PATH = "/ws/NGTPA/v1/TRPPwdValidation";
  private static final String OK =
      "{\"response\":{\"err-message\":\"\",\"data\":[{\"success\":true}]}}";
  private static final PasswordCheckCommand COMMAND =
      new PasswordCheckCommand("test-customer", " TestPassword ", ActorUserRole.ROLE_INPUT, "DB");

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void invalidTokenNeverResubmitsPassword(boolean encrypted) throws Exception {
    var h = new Harness(encrypted, "invalid_token");
    assertThat(h.check()).isEqualTo(PasswordCheckResult.UNAVAILABLE);
    h.assertCounts(1);
    verify(h.tokens, never()).refreshAccessToken(any());
    verify(h.tokens, never()).evictToken(anyString());
  }

  @Test
  void encryptedInvalidPublicKeyNeverRefreshesOrResubmits() throws Exception {
    var h = new Harness(true, "invalid_public_key");
    assertThat(h.check()).isEqualTo(PasswordCheckResult.UNAVAILABLE);
    h.assertCounts(1);
    verify(h.certificates, never()).refreshPublicKey(any());
    verify(h.certificates, never()).evictCertificate(anyString());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void everyTechnicalPathHasAtMostOnePasswordSubmission(boolean encrypted) throws Exception {
    for (String scenario :
        List.of("503", "transport", "timeout", "malformed", "trailing", "201", "crypto-response")) {
      var h = new Harness(encrypted, scenario);
      assertThat(h.check()).as(scenario).isEqualTo(PasswordCheckResult.UNAVAILABLE);
      h.assertCounts(1);
    }
    var beforeSend = new Harness(encrypted, "crypto-request");
    assertThat(beforeSend.check()).isEqualTo(PasswordCheckResult.UNAVAILABLE);
    beforeSend.assertCounts(0);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void realConnectorNeverResubmitsAfterServerResponseResetOrTimeout(boolean encrypted)
      throws Exception {
    for (String scenario :
        List.of("ok", "invalid_token", "invalid_public_key", "503", "reset", "wire-timeout")) {
      AtomicInteger receivedPasswordPosts = new AtomicInteger();
      var server =
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
                                assertThat(request.requestHeaders().names())
                                    .noneMatch(
                                        name ->
                                            name.equalsIgnoreCase("accountEnv")
                                                || name.equalsIgnoreCase("env"));
                                assertThat(body).doesNotContain("accountEnv", "\"env\"");
                                receivedPasswordPosts.incrementAndGet();
                                if (encrypted) {
                                  assertThat(body).doesNotContain("TestPassword", "test-customer");
                                } else {
                                  assertThat(body).contains("\"password\":\" TestPassword \"");
                                }
                                if (scenario.equals("reset")) {
                                  response.withConnection(Disposable::dispose);
                                  return Mono.empty();
                                }
                                if (scenario.equals("wire-timeout")) {
                                  return Mono.delay(Duration.ofSeconds(1))
                                      .then(response.sendString(Mono.just(OK)).then());
                                }
                                if (scenario.equals("503")) {
                                  return response
                                      .status(503)
                                      .sendString(Mono.just("unavailable"))
                                      .then();
                                }
                                if (scenario.startsWith("invalid_")) {
                                  return response
                                      .status(401)
                                      .sendString(Mono.just("{\"error\":\"" + scenario + "\"}"))
                                      .then();
                                }
                                return response.sendString(Mono.just(OK)).then();
                              }))
              .bindNow();
      try {
        var h = new Harness(encrypted, scenario, "http://127.0.0.1:" + server.port());
        assertThat(h.check())
            .as(scenario)
            .isEqualTo(
                scenario.equals("ok")
                    ? PasswordCheckResult.CORRECT
                    : PasswordCheckResult.UNAVAILABLE);
        assertThat(receivedPasswordPosts).as(scenario).hasValue(1);
        verify(h.tokens, times(1)).getAccessToken(any());
        verify(h.tokens, never()).refreshAccessToken(any());
        verify(h.certificates, never()).refreshPublicKey(any());
      } finally {
        server.disposeNow();
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void successUsesAccountEnvironmentProfileExactRequestEncryptionAndSuccessfulVerificationKey(
      boolean encrypted) throws Exception {
    var h = new Harness(encrypted, "ok");
    assertThat(h.check()).isEqualTo(PasswordCheckResult.CORRECT);
    h.assertCounts(1);
    assertThat(h.requests.getFirst().url()).isEqualTo(URI.create("https://db.test" + PATH));
    assertThat(h.requests.getFirst().headers().getFirst("Ocp-Apim-Subscription-Key"))
        .isEqualTo("test-profile-key");
    verify(h.tokens).getAccessToken(argThat(profile -> profile.profileId().equals("db")));
    verify(h.certificates, times(encrypted ? 1 : 0))
        .getPublicKey(argThat(profile -> profile.profileId().equals("db")));
    assertThat(h.requests.getFirst().headers().headerNames())
        .noneMatch(name -> name.equalsIgnoreCase("accountEnv") || name.equalsIgnoreCase("env"));
    verify(h.crypto)
        .encryptRequest(
            PATH,
            new TrpPwdValidationRequest("test-customer", " TestPassword "),
            TrpPwdValidationRequest.class,
            encrypted ? h.key : null);
    verify(h.crypto)
        .decryptResponseEnvelope(
            PATH, OK, TrpPwdValidationDataItem.class, encrypted ? h.key : null);
    if (encrypted) {
      verify(h.rsa).createSignedJwt(eq(" TestPassword "), any());
      verify(h.rsa, times(2)).encryptPlaintext(anyString(), eq(h.key));
    } else {
      verifyNoInteractions(h.rsa);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void strictExchangeNeverLogsBodyIdentityPasswordOrUnknownResponseText(boolean encrypted)
      throws Exception {
    Logger logger = (Logger) LoggerFactory.getLogger("com.bct.ngtpa.apiservice.adapter.out.apim");
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      for (String scenario : List.of("ok", "unknown", "malformed", "503")) {
        new Harness(encrypted, scenario).check();
      }
      assertThat(appender.list).isNotEmpty();
      assertThat(appender.list)
          .allSatisfy(
              event ->
                  assertThat(event.getFormattedMessage())
                      .doesNotContain(
                          "test-customer", "TestPassword", "test-private-response-detail"));
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void defaultsPreserveRecoveryAndStrictMetadataRejectsConflictingBusinessRetry() {
    var body = new TrpPwdValidationRequest("test", "test");
    var context = new ApimCredentialResolutionContext(ActorUserRole.ROLE_INPUT, "DB");
    var defaultRequest =
        new ApimExchangeRequest<>(
            PATH,
            body,
            TrpPwdValidationRequest.class,
            TrpPwdValidationDataItem.class,
            ApimDatabaseScope.ACCOUNT_ENV,
            context,
            ApimRetryEligibility.NON_RETRYABLE);
    assertThat(defaultRequest.resubmissionPolicy())
        .isEqualTo(ApimResubmissionPolicy.ALLOW_RECOVERY);
    assertThatThrownBy(
            () ->
                new ApimExchangeRequest<>(
                    PATH,
                    body,
                    TrpPwdValidationRequest.class,
                    TrpPwdValidationDataItem.class,
                    ApimDatabaseScope.ACCOUNT_ENV,
                    context,
                    ApimRetryEligibility.SAFE_BUSINESS_CALL,
                    ApimResubmissionPolicy.NEVER))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new ApimExchangeRequest<>(
                    PATH,
                    body,
                    TrpPwdValidationRequest.class,
                    TrpPwdValidationDataItem.class,
                    ApimDatabaseScope.ACCOUNT_ENV,
                    context,
                    ApimRetryEligibility.NON_RETRYABLE,
                    null))
        .isInstanceOf(NullPointerException.class);
  }

  private static final class Harness {
    final AtomicInteger passwordPosts = new AtomicInteger();
    final List<ClientRequest> requests = new ArrayList<>();
    final ApimTokenService tokens = mock(ApimTokenService.class);
    final ApimCertificateService certificates = mock(ApimCertificateService.class);
    final ApimRsaPayloadCrypto rsa = mock(ApimRsaPayloadCrypto.class);
    final ApimPayloadCryptoService crypto;
    final PublicKey key;
    final ApimPasswordValidationAdapter adapter;
    final boolean encrypted;

    Harness(boolean encrypted, String scenario) throws Exception {
      this(encrypted, scenario, null);
    }

    Harness(boolean encrypted, String scenario, String loopbackBaseUrl) throws Exception {
      this.encrypted = encrypted;
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      var pair = generator.generateKeyPair();
      key = pair.getPublic();
      var properties = new ApimProperties();
      var passwordValidationProperties = new PasswordValidationApimProperties();
      passwordValidationProperties.setPath(PATH);
      properties.getEncryption().setEnabled(encrypted);
      properties.getEncryption().setRequestFields(List.of("cust-id", "password"));
      properties.getRetry().getBusinessCall().setMaxAttempts(3);
      properties.getRetry().getBusinessCall().setBackoffMilliseconds(0);
      properties.getRouting().setNonAccountProfileId("non-account");
      properties.getRouting().setAccountEnvProfileMappings(Map.of("DB", "db"));
      var nonAccount = new ApimProperties.CredentialProfiles.Profile();
      nonAccount.setProfileId("non-account");
      nonAccount.setBaseUrl("https://non-account.test");
      nonAccount.setApiKey("wrong-profile-key");
      var profile = new ApimProperties.CredentialProfiles.Profile();
      profile.setProfileId("db");
      profile.setBaseUrl("https://db.test");
      if (loopbackBaseUrl != null) {
        profile.setBaseUrl(loopbackBaseUrl);
        properties.setTimeoutMilliseconds(scenario.equals("wire-timeout") ? 100 : 5000);
      }
      profile.setApiKey("test-profile-key");
      properties.getCredentialProfiles().setProfiles(List.of(nonAccount, profile));
      when(tokens.getAccessToken(any())).thenReturn(Mono.just("test-access-value"));
      when(certificates.getPublicKey(any())).thenReturn(Mono.just(key));
      var appCertificate = mock(ApimAppCertificateService.class);
      when(appCertificate.getAppPrivateKey()).thenReturn(pair.getPrivate());
      when(rsa.createSignedJwt(anyString(), any())).thenReturn("test-signed-value");
      when(rsa.encryptPlaintext(anyString(), eq(key))).thenReturn("test-encrypted-key");
      crypto =
          spy(
              new ApimPayloadCryptoService(
                  properties,
                  new ObjectMapper(),
                  new ApimPayloadFieldTransformer(),
                  rsa,
                  new ApimAesKeyFactory(),
                  appCertificate));
      if (scenario.equals("crypto-request")) {
        doThrow(new IllegalStateException("test-private-response-detail"))
            .when(crypto)
            .encryptRequest(eq(PATH), any(), eq(TrpPwdValidationRequest.class), any());
      }
      if (scenario.equals("crypto-response")) {
        doThrow(new IllegalStateException("test-private-response-detail"))
            .when(crypto)
            .decryptResponseEnvelope(
                eq(PATH), anyString(), eq(TrpPwdValidationDataItem.class), any());
      }
      WebClient.Builder builder =
          WebClient.builder()
              .exchangeFunction(
                  request -> {
                    assertThat(request.url().getPath()).isEqualTo(PATH);
                    requests.add(request);
                    passwordPosts.incrementAndGet();
                    return switch (scenario) {
                      case "transport" ->
                          Mono.error(
                              new WebClientRequestException(
                                  new java.io.IOException("test transport"),
                                  request.method(),
                                  request.url(),
                                  new HttpHeaders()));
                      case "timeout" ->
                          Mono.delay(Duration.ofMillis(20))
                              .then(Mono.error(new TimeoutException("test timeout")));
                      case "503" ->
                          response(HttpStatus.SERVICE_UNAVAILABLE, "test-private-response-detail");
                      case "201" -> response(HttpStatus.CREATED, OK);
                      case "invalid_token", "invalid_public_key" ->
                          response(HttpStatus.UNAUTHORIZED, "{\"error\":\"" + scenario + "\"}");
                      case "malformed" -> response(HttpStatus.OK, "{test-private-response-detail");
                      case "trailing" -> response(HttpStatus.OK, OK + " trailing");
                      case "unknown" ->
                          response(
                              HttpStatus.OK,
                              "{\"response\":{\"err-message\":\"test-private-response-detail\"}}");
                      default -> response(HttpStatus.OK, OK);
                    };
                  });
      if (loopbackBaseUrl != null) {
        builder = WebClient.builder();
      }
      var facade =
          new ApimWebClientFacade(
              WebClient.create(),
              builder,
              new ObjectMapper(),
              new DefaultApimCredentialProfileResolver(properties),
              tokens,
              certificates,
              properties);
      adapter =
          new ApimPasswordValidationAdapter(
              new DefaultApimExchangeExecutor(facade, crypto, properties),
              passwordValidationProperties);
    }

    PasswordCheckResult check() {
      return adapter.check(COMMAND).block();
    }

    void assertCounts(int posts) {
      assertThat(passwordPosts).hasValue(posts);
      verify(tokens, times(posts == 0 ? 0 : 1)).getAccessToken(any());
      verify(certificates, times(encrypted ? 1 : 0)).getPublicKey(any());
      verify(tokens, never()).refreshAccessToken(any());
      verify(certificates, never()).refreshPublicKey(any());
    }

    private static Mono<ClientResponse> response(HttpStatus status, String body) {
      return Mono.just(ClientResponse.create(status).body(body).build());
    }
  }
}
