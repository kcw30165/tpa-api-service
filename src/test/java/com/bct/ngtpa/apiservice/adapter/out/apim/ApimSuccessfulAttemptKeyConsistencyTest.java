package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfile;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfileResolver;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimSuccessfulExchange;
import com.bct.ngtpa.apiservice.adapter.out.apim.oauth.ApimTokenCacheEntry;
import com.bct.ngtpa.apiservice.adapter.out.apim.oauth.ApimTokenService;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class ApimSuccessfulAttemptKeyConsistencyTest {
  private static final ApimCredentialProfile PROFILE =
      new ApimCredentialProfile(
          "profile-1",
          "client",
          "secret",
          "api-key",
          "https://token.test",
          "https://apim.test",
          List.of(),
          "/certificate");
  private static final ApimCredentialResolutionContext CONTEXT =
      new ApimCredentialResolutionContext(ActorUserRole.ROLE_USER_MBR, null);

  @Test
  void failFirstSuccessfulExchangeApiMustExist() throws Exception {
    assertThat(
            ApimWebClientFacade.class.getMethod(
                "postEncryptedSuccessfulExchange",
                String.class,
                java.util.function.BiFunction.class,
                ApimDatabaseScope.class,
                ApimCredentialResolutionContext.class,
                ApimRetryEligibility.class))
        .isNotNull();
  }

  @Test
  void encryptedSuccessReturnsExactKeyUsedForSuccessfulRequestAndResolvesProfileOnce() {
    TestKey key = new TestKey("key-1");
    TrackingCertificateService certificates = new TrackingCertificateService(key);
    AtomicInteger resolutions = new AtomicInteger();
    ApimWebClientFacade facade =
        facade(req -> ok("success"), certificates, resolutions, new AtomicReference<>("token"));
    AtomicReference<PublicKey> encryptedWith = new AtomicReference<>();

    ApimSuccessfulExchange result =
        facade
            .postEncryptedSuccessfulExchange(
                "/encrypted",
                (profile, publicKey) -> {
                  encryptedWith.set(publicKey);
                  return "cipher-1";
                },
                ApimDatabaseScope.NON_ACCOUNT,
                CONTEXT,
                ApimRetryEligibility.NON_RETRYABLE)
            .block();

    assertThat(result.responseBody()).isEqualTo("success");
    assertThat(result.profile()).isSameAs(PROFILE);
    assertThat(result.responseVerificationKey()).isSameAs(key).isSameAs(encryptedWith.get());
    assertThat(resolutions).hasValue(1);
  }

  @Test
  void invalidPublicKeyRefreshesRegeneratesAndReturnsRefreshedSuccessfulKey() {
    TestKey first = new TestKey("key-1");
    TestKey refreshed = new TestKey("key-2");
    TrackingCertificateService certificates = new TrackingCertificateService(first, refreshed);
    AtomicInteger calls = new AtomicInteger();
    List<String> payloads = new ArrayList<>();
    List<PublicKey> keys = new ArrayList<>();
    ApimWebClientFacade facade =
        facade(
            req ->
                calls.getAndIncrement() == 0
                    ? error(HttpStatus.BAD_REQUEST, "{\"error\":\"invalid_public_key\"}")
                    : ok("success"),
            certificates,
            new AtomicInteger(),
            new AtomicReference<>("token"));

    ApimSuccessfulExchange result =
        facade
            .postEncryptedSuccessfulExchange(
                "/encrypted",
                (profile, key) -> {
                  keys.add(key);
                  String body = "cipher-" + keys.size();
                  payloads.add(body);
                  return body;
                },
                ApimDatabaseScope.NON_ACCOUNT,
                CONTEXT,
                ApimRetryEligibility.NON_RETRYABLE)
            .block();

    assertThat(payloads).containsExactly("cipher-1", "cipher-2");
    assertThat(keys).containsExactly(first, refreshed);
    assertThat(certificates.evictions).isEqualTo(1);
    assertThat(certificates.refreshes).isEqualTo(1);
    assertThat(result.responseVerificationKey()).isSameAs(refreshed);
  }

  @Test
  void tokenRefreshKeepsSameProfileRequestAndKey() {
    TestKey key = new TestKey("key-1");
    AtomicReference<String> token = new AtomicReference<>("old-token");
    List<String> bodies = new ArrayList<>();
    List<PublicKey> keys = new ArrayList<>();
    ApimWebClientFacade facade =
        facade(
            req ->
                "Bearer old-token".equals(req.headers().getFirst("Authorization"))
                    ? error(HttpStatus.UNAUTHORIZED, "{\"error\":\"invalid_token\"}")
                    : ok("success"),
            new TrackingCertificateService(key),
            new AtomicInteger(),
            token);

    ApimSuccessfulExchange result =
        facade
            .postEncryptedSuccessfulExchange(
                "/encrypted",
                (profile, k) -> {
                  keys.add(k);
                  bodies.add("cipher");
                  return "cipher";
                },
                ApimDatabaseScope.NON_ACCOUNT,
                CONTEXT,
                ApimRetryEligibility.NON_RETRYABLE)
            .block();

    assertThat(bodies).containsExactly("cipher");
    assertThat(keys).containsExactly(key);
    assertThat(result.profile()).isSameAs(PROFILE);
    assertThat(result.responseVerificationKey()).isSameAs(key);
  }

  @Test
  void businessRetryBuildsFreshEncryptedAttemptAndReturnsFinalAttemptKey() {
    TestKey first = new TestKey("key-1");
    TestKey second = new TestKey("key-2");
    TrackingCertificateService certificates = new TrackingCertificateService(first, second);
    AtomicInteger calls = new AtomicInteger();
    List<PublicKey> keys = new ArrayList<>();
    ApimProperties properties = properties();
    properties.getRetry().getBusinessCall().setBackoffMilliseconds(0);
    ApimWebClientFacade facade =
        facade(
            req ->
                calls.getAndIncrement() == 0
                    ? error(HttpStatus.SERVICE_UNAVAILABLE, "unavailable")
                    : ok("success"),
            certificates,
            new AtomicInteger(),
            new AtomicReference<>("token"),
            properties);

    ApimSuccessfulExchange result =
        facade
            .postEncryptedSuccessfulExchange(
                "/encrypted",
                (profile, key) -> {
                  keys.add(key);
                  return "cipher-" + keys.size();
                },
                ApimDatabaseScope.NON_ACCOUNT,
                CONTEXT,
                ApimRetryEligibility.SAFE_BUSINESS_CALL)
            .block();

    assertThat(keys).containsExactly(first, second);
    assertThat(result.responseVerificationKey()).isSameAs(second);
  }

  @Test
  void plainSuccessReturnsResolvedProfileAndNullKey() {
    AtomicInteger resolutions = new AtomicInteger();
    ApimSuccessfulExchange result =
        facade(
                req -> ok("plain"),
                new TrackingCertificateService(),
                resolutions,
                new AtomicReference<>("token"))
            .postSuccessfulExchange(
                "/plain",
                "body",
                ApimDatabaseScope.NON_ACCOUNT,
                CONTEXT,
                ApimRetryEligibility.NON_RETRYABLE)
            .block();
    assertThat(result.responseBody()).isEqualTo("plain");
    assertThat(result.profile()).isSameAs(PROFILE);
    assertThat(result.responseVerificationKey()).isNull();
    assertThat(resolutions).hasValue(1);
  }

  private ApimWebClientFacade facade(
      ExchangeFunction exchange,
      TrackingCertificateService certificates,
      AtomicInteger resolutions,
      AtomicReference<String> token) {
    return facade(exchange, certificates, resolutions, token, properties());
  }

  private ApimWebClientFacade facade(
      ExchangeFunction exchange,
      TrackingCertificateService certificates,
      AtomicInteger resolutions,
      AtomicReference<String> token,
      ApimProperties properties) {
    WebClient.Builder builder = WebClient.builder().exchangeFunction(exchange);
    ApimCredentialProfileResolver resolver =
        (scope, context) -> {
          resolutions.incrementAndGet();
          return Mono.just(PROFILE);
        };
    ApimTokenService tokens = mock(ApimTokenService.class);
    when(tokens.getAccessToken(any())).thenAnswer(inv -> Mono.just(token.get()));
    when(tokens.refreshAccessToken(any()))
        .thenAnswer(
            inv -> {
              token.set("new-token");
              return Mono.just(
                  new ApimTokenCacheEntry(
                      PROFILE.profileId(),
                      "new-token",
                      "Bearer",
                      System.currentTimeMillis() + 3600000L,
                      null));
            });
    return new ApimWebClientFacade(
        WebClient.create(),
        builder,
        new ObjectMapper(),
        resolver,
        tokens,
        certificates,
        properties);
  }

  private static ApimProperties properties() {
    ApimProperties p = new ApimProperties();
    p.getRetry().getBusinessCall().setMaxAttempts(3);
    return p;
  }

  private static Mono<ClientResponse> ok(String body) {
    return Mono.just(ClientResponse.create(HttpStatus.OK).body(body).build());
  }

  private static Mono<ClientResponse> error(HttpStatus status, String body) {
    return Mono.just(ClientResponse.create(status).body(body).build());
  }

  private static final class TrackingCertificateService extends ApimCertificateService {
    private final List<PublicKey> keys;
    private int reads;
    private int evictions;
    private int refreshes;

    TrackingCertificateService(PublicKey... keys) {
      super(null, new ApimProperties(), null);
      this.keys = List.of(keys);
    }

    @Override
    public Mono<PublicKey> getPublicKey(ApimCredentialProfile profile) {
      PublicKey key = keys.get(Math.min(reads, keys.size() - 1));
      reads++;
      return Mono.just(key);
    }

    @Override
    public Mono<PublicKey> refreshPublicKey(ApimCredentialProfile profile) {
      refreshes++;
      return Mono.just(keys.get(keys.size() - 1));
    }

    @Override
    public void evictCertificate(String profileId) {
      evictions++;
    }
  }

  private record TestKey(String id) implements PublicKey {
    public String getAlgorithm() {
      return "RSA";
    }

    public String getFormat() {
      return "X.509";
    }

    public byte[] getEncoded() {
      return id.getBytes();
    }
  }
}
