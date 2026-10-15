package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.client.ApimContextPropagationExchangeFilter;
import com.bct.ngtpa.apiservice.adapter.out.apim.client.ApimRequestLoggingExchangeFilter;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfile;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfileResolver;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimErrorResponse;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimSuccessfulExchange;
import com.bct.ngtpa.apiservice.adapter.out.apim.oauth.ApimTokenService;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

/**
 * APIM HTTP transport facade. - Resolves credential profile per operation - Attaches API key and
 * Authorization header per-profile - Evicts and retries on `invalid_token` and `invalid_public_key`
 * when appropriate
 */
@Slf4j
@Service
public class ApimWebClientFacade {

  private final WebClient apimWebClient;
  private final WebClient.Builder webClientBuilder;
  private final ObjectMapper objectMapper;
  private final ApimCredentialProfileResolver profileResolver;
  private final ApimTokenService apimTokenService;
  private final ApimCertificateService apimCertificateService;
  private final ApimProperties apimProperties;
  private final ApimAppCertificateService apimAppCertificateService;
  private final LoggingSanitizer loggingSanitizer;
  private final ApimContextPropagationExchangeFilter contextPropagationExchangeFilter;
  private final ApimRequestLoggingExchangeFilter requestLoggingExchangeFilter;

  @Autowired
  public ApimWebClientFacade(
      WebClient apimWebClient,
      WebClient.Builder webClientBuilder,
      ObjectMapper objectMapper,
      ApimCredentialProfileResolver profileResolver,
      ApimTokenService apimTokenService,
      ApimCertificateService apimCertificateService,
      ApimProperties apimProperties,
      ApimAppCertificateService apimAppCertificateService,
      LoggingSanitizer loggingSanitizer,
      ApimContextPropagationExchangeFilter contextPropagationExchangeFilter,
      ApimRequestLoggingExchangeFilter requestLoggingExchangeFilter) {
    this.apimWebClient = apimWebClient;
    this.webClientBuilder = webClientBuilder;
    this.objectMapper = objectMapper;
    this.profileResolver = profileResolver;
    this.apimTokenService = apimTokenService;
    this.apimCertificateService = apimCertificateService;
    this.apimProperties = apimProperties;
    this.apimAppCertificateService = apimAppCertificateService;
    this.loggingSanitizer = loggingSanitizer;
    this.contextPropagationExchangeFilter = contextPropagationExchangeFilter;
    this.requestLoggingExchangeFilter = requestLoggingExchangeFilter;
  }

  public ApimWebClientFacade(
      WebClient apimWebClient,
      WebClient.Builder webClientBuilder,
      ObjectMapper objectMapper,
      ApimCredentialProfileResolver profileResolver,
      ApimTokenService apimTokenService,
      ApimCertificateService apimCertificateService,
      ApimProperties apimProperties) {
    this(
        apimWebClient,
        webClientBuilder,
        objectMapper,
        profileResolver,
        apimTokenService,
        apimCertificateService,
        apimProperties,
        null,
        new LoggingSanitizer(objectMapper, new MaskingService(new MaskingProperties())),
        new ApimContextPropagationExchangeFilter(),
        new ApimRequestLoggingExchangeFilter(
            new LoggingSanitizer(objectMapper, new MaskingService(new MaskingProperties())),
            objectMapper));
  }

  public Mono<String> post(
      String path,
      Object requestBody,
      ApimDatabaseScope scope,
      ApimCredentialResolutionContext resolutionContext,
      ApimRetryEligibility retryEligibility) {
    ApimRetryEligibility eligibility =
        retryEligibility == null ? ApimRetryEligibility.NON_RETRYABLE : retryEligibility;
    apimProperties.validateRetryPolicy();
    return postSuccessfulExchange(path, requestBody, scope, resolutionContext, eligibility)
        .map(ApimSuccessfulExchange::responseBody);
  }

  public Mono<ApimSuccessfulExchange> postSuccessfulExchange(
      String path,
      Object requestBody,
      ApimDatabaseScope scope,
      ApimCredentialResolutionContext resolutionContext,
      ApimRetryEligibility retryEligibility) {
    ApimRetryEligibility eligibility =
        retryEligibility == null ? ApimRetryEligibility.NON_RETRYABLE : retryEligibility;
    apimProperties.validateRetryPolicy();
    return resolveProfile(scope, resolutionContext)
        .flatMap(
            profile ->
                sendBusinessAttempt(profile, path, requestBody, eligibility, 1)
                    .map(body -> new ApimSuccessfulExchange(body, profile, null)));
  }

  /** Resolve profile using facade-owned resolution logic (centralised logging and errors). */
  public Mono<ApimCredentialProfile> resolveProfile(
      ApimDatabaseScope scope, ApimCredentialResolutionContext resolutionContext) {
    return profileResolver
        .resolve(scope, resolutionContext)
        .switchIfEmpty(
            Mono.defer(
                () -> {
                  logProfileResolutionFailure(
                      scope, resolutionContext, null, "profile resolver returned empty");
                  return Mono.error(
                      new com.bct.ngtpa.apiservice.exception.ApimProfileResolutionException(
                          "APIM profile not found for given routing context"));
                }))
        .onErrorMap(
            err -> {
              logProfileResolutionFailure(
                  scope, resolutionContext, null, err == null ? "unknown" : err.getMessage());
              return err
                      instanceof com.bct.ngtpa.apiservice.exception.ApimProfileResolutionException
                  ? err
                  : new com.bct.ngtpa.apiservice.exception.ApimProfileResolutionException(
                      "APIM profile resolution failed", err);
            });
  }

  /**
   * Allow callers to supply an already-resolved profile. This preserves single-resolution-per-call
   * semantics and is used by callers that need to perform per-profile encryption using the
   * profile's certificate.
   */
  public Mono<String> post(
      String path,
      Object requestBody,
      ApimCredentialProfile profile,
      ApimRetryEligibility retryEligibility) {
    ApimRetryEligibility eligibility =
        retryEligibility == null ? ApimRetryEligibility.NON_RETRYABLE : retryEligibility;
    apimProperties.validateRetryPolicy();
    return sendBusinessAttempt(profile, path, requestBody, eligibility, 1);
  }

  /**
   * Post with facade-owned encryption function. Facade resolves the profile, fetches the profile
   * certificate, invokes the encryptFn to obtain a fresh encrypted payload for each attempt, and
   * transmits while preserving same-profile routing for token, certificate, headers and retries.
   */
  public Mono<String> postEncrypted(
      String path,
      java.util.function.BiFunction<ApimCredentialProfile, java.security.PublicKey, Object>
          encryptFn,
      ApimDatabaseScope scope,
      ApimCredentialResolutionContext resolutionContext,
      ApimRetryEligibility retryEligibility) {
    ApimRetryEligibility eligibility =
        retryEligibility == null ? ApimRetryEligibility.NON_RETRYABLE : retryEligibility;
    apimProperties.validateRetryPolicy();
    return postEncryptedSuccessfulExchange(path, encryptFn, scope, resolutionContext, eligibility)
        .map(ApimSuccessfulExchange::responseBody);
  }

  public Mono<ApimSuccessfulExchange> postEncryptedSuccessfulExchange(
      String path,
      java.util.function.BiFunction<ApimCredentialProfile, java.security.PublicKey, Object>
          encryptFn,
      ApimDatabaseScope scope,
      ApimCredentialResolutionContext resolutionContext,
      ApimRetryEligibility retryEligibility) {
    ApimRetryEligibility eligibility =
        retryEligibility == null ? ApimRetryEligibility.NON_RETRYABLE : retryEligibility;
    apimProperties.validateRetryPolicy();
    return resolveProfile(scope, resolutionContext)
        .flatMap(
            profile ->
                sendBusinessAttemptEncryptedSuccessful(profile, path, encryptFn, eligibility, 1));
  }

  private Mono<String> sendBusinessAttempt(
      ApimCredentialProfile profile,
      String path,
      Object requestBody,
      ApimRetryEligibility retryEligibility,
      int attempt) {
    return sendWithProfile(profile, path, requestBody, new RetryContext())
        .onErrorResume(
            error -> {
              int maxAttempts = apimProperties.getRetry().getBusinessCall().getMaxAttempts();
              if (!retryEligibility.retryEligible()
                  || attempt >= maxAttempts
                  || !isTransientBusinessFailure(error)) {
                return Mono.error(error);
              }
              long backoffMilliseconds =
                  apimProperties.getRetry().getBusinessCall().getBackoffMilliseconds();
              log.warn(
                  "Retrying transient APIM business call path={} attempt={}/{}",
                  path,
                  attempt + 1,
                  maxAttempts);
              Mono<Long> delay =
                  backoffMilliseconds == 0
                      ? Mono.just(0L)
                      : Mono.delay(Duration.ofMillis(backoffMilliseconds));
              return delay.then(
                  sendBusinessAttempt(profile, path, requestBody, retryEligibility, attempt + 1));
            });
  }

  private Mono<ApimSuccessfulExchange> sendBusinessAttemptEncryptedSuccessful(
      ApimCredentialProfile profile,
      String path,
      java.util.function.BiFunction<ApimCredentialProfile, java.security.PublicKey, Object>
          encryptFn,
      ApimRetryEligibility retryEligibility,
      int attempt) {
    return apimCertificateService
        .getPublicKey(profile)
        .flatMap(
            key -> {
              Object encryptedRequest = encryptFn.apply(profile, key);
              RetryContext context = new RetryContext();
              context.encryptionFunction = encryptFn;
              context.responseVerificationKey = key;
              return sendWithProfile(profile, path, encryptedRequest, context)
                  .map(
                      body ->
                          new ApimSuccessfulExchange(
                              body, profile, context.responseVerificationKey));
            })
        .onErrorResume(
            error -> {
              int maxAttempts = apimProperties.getRetry().getBusinessCall().getMaxAttempts();
              if (!retryEligibility.retryEligible()
                  || attempt >= maxAttempts
                  || !isTransientBusinessFailure(error)) {
                return Mono.error(error);
              }
              long backoffMilliseconds =
                  apimProperties.getRetry().getBusinessCall().getBackoffMilliseconds();
              log.warn(
                  "Retrying transient encrypted APIM business call path={} attempt={}/{}",
                  path,
                  attempt + 1,
                  maxAttempts);
              Mono<Long> delay =
                  backoffMilliseconds == 0
                      ? Mono.just(0L)
                      : Mono.delay(Duration.ofMillis(backoffMilliseconds));
              return delay.then(
                  sendBusinessAttemptEncryptedSuccessful(
                      profile, path, encryptFn, retryEligibility, attempt + 1));
            });
  }

  private static final Pattern TRANSIENT_STATUS_PATTERN =
      Pattern.compile("status=(500|501|502|503|504|505|506|507|508|510|511)(?:\\s|\\b)");

  private boolean isTransientBusinessFailure(Throwable error) {
    if (error instanceof ApimException apimException) {
      String message = apimException.getMessage();
      return message != null && TRANSIENT_STATUS_PATTERN.matcher(message).find();
    }
    if (error instanceof WebClientRequestException || error instanceof TimeoutException) {
      return true;
    }
    Throwable cause = error.getCause();
    return cause != null && cause != error && isTransientBusinessFailure(cause);
  }

  private Mono<String> sendWithProfile(
      ApimCredentialProfile profile, String path, Object requestBody, RetryContext retryContext) {
    // Ensure required per-profile fields are present — do not fallback to global properties
    if (profile == null || profile.profileId() == null || profile.profileId().isBlank()) {
      return Mono.error(
          new com.bct.ngtpa.apiservice.exception.ApimProfileResolutionException(
              "Profile id missing"));
    }
    if (profile.baseUrl() == null || profile.baseUrl().isBlank()) {
      return Mono.error(
          new com.bct.ngtpa.apiservice.exception.ApimProfileResolutionException(
              "Profile baseUrl missing for profile=" + profile.profileId()));
    }
    if (profile.apiKey() == null || profile.apiKey().isBlank()) {
      return Mono.error(
          new com.bct.ngtpa.apiservice.exception.ApimProfileResolutionException(
              "Profile apiKey missing for profile=" + profile.profileId()));
    }

    return apimTokenService
        .getAccessToken(profile)
        .flatMap(
            token -> {
              String baseUrl = profile.baseUrl();
              WebClient client =
                  webClientBuilder
                      .clone()
                      .baseUrl(baseUrl)
                      .filter(contextPropagationExchangeFilter.filter())
                      .filter(requestLoggingExchangeFilter.filter())
                      .codecs(responseSizeLimit(apimProperties))
                      .build();

              WebClient.RequestBodySpec request =
                  client
                      .post()
                      .uri(uriBuilder -> uriBuilder.path(path).build())
                      .header(apimProperties.getApiKeyHeaderName(), profile.apiKey())
                      .header("Authorization", "Bearer " + token);

              String certificateHeaderValue = getCertificateHeaderValue();
              if (apimProperties.getEncryption().isEnabled()
                  && StringUtils.hasText(certificateHeaderValue)) {
                request.header("Certificate", certificateHeaderValue);
              }

              WebClient.RequestHeadersSpec<?> req = request.bodyValue(requestBody);

              logRequest(path, requestBody);
              return req.exchangeToMono(
                  response -> handleResponse(response, profile, path, requestBody, retryContext));
            });
  }

  /**
   * Codec limit for buffered APIM response bodies, from apim.max-response-size. Applied to the
   * cloned builder only, so the shared WebClient.Builder (token, certificate, SMS) is unchanged.
   */
  static java.util.function.Consumer<org.springframework.http.codec.ClientCodecConfigurer>
      responseSizeLimit(ApimProperties properties) {
    int maxBytes = properties.maxResponseSizeBytes();
    return codecs -> codecs.defaultCodecs().maxInMemorySize(maxBytes);
  }

  protected String getCertificateHeaderValue() {
    return apimAppCertificateService == null
        ? null
        : apimAppCertificateService.getCertificateHeaderValue();
  }

  private Mono<String> handleResponse(
      ClientResponse response,
      ApimCredentialProfile profile,
      String path,
      Object requestBody,
      RetryContext retryContext) {
    HttpStatusCode statusCode = response.statusCode();
    return response
        .bodyToMono(String.class)
        .defaultIfEmpty("")
        .flatMap(
            body -> {
              if (statusCode.isError()) {
                String errorCode = extractApimErrorCode(body);
                if ("invalid_token".equals(errorCode)) {
                  if (!retryContext.tokenRetry) {
                    retryContext.tokenRetry = true;
                    log.info(
                        "APIM returned invalid_token for profileId={}; evicting cached token and retrying once.",
                        profile.profileId());
                    apimTokenService.evictToken(profile.profileId());
                    return apimTokenService
                        .refreshAccessToken(profile)
                        .flatMap(e -> sendWithProfile(profile, path, requestBody, retryContext));
                  }
                }
                if ("invalid_public_key".equals(errorCode)) {
                  if (!retryContext.certificateRetry) {
                    retryContext.certificateRetry = true;
                    apimCertificateService.evictCertificate(profile.profileId());
                    // If we have an encryption function attached to the retry context, obtain
                    // a fresh public key and regenerate the encrypted payload before retrying.
                    if (retryContext.encryptionFunction != null) {
                      return apimCertificateService
                          .refreshPublicKey(profile)
                          .flatMap(
                              newPk -> {
                                retryContext.responseVerificationKey = newPk;
                                Object newRequest =
                                    retryContext.encryptionFunction.apply(profile, newPk);
                                return sendWithProfile(profile, path, newRequest, retryContext);
                              });
                    }
                    // A body-only call cannot safely regenerate encrypted ciphertext.
                    // Do not retry stale requestBody after certificate invalidation.
                  }
                }

                String sanitizedBody = loggingSanitizer.sanitizeText(body);
                log.error(
                    "APIM request failed with status={} body={}",
                    statusCode.value(),
                    sanitizedBody);
                return Mono.error(
                    new ApimException(
                        HttpStatus.BAD_GATEWAY,
                        mapUpstreamErrorCode(statusCode),
                        "APIM request failed with status=%s body=%s"
                            .formatted(statusCode.value(), sanitizedBody)));
              }
              return Mono.just(body);
            });
  }

  private String extractApimErrorCode(String body) {
    try {
      ApimErrorResponse err = objectMapper.readValue(body, ApimErrorResponse.class);
      return err.error();
    } catch (Exception ex) {
      log.debug("Unable to parse APIM error body as ApimErrorResponse.", ex);
      if (body.contains("\"error\":\"invalid_token\"")) {
        return "invalid_token";
      }
      if (body.contains("\"error\":\"invalid_public_key\"")) {
        return "invalid_public_key";
      }
      return null;
    }
  }

  private void logRequest(String path, Object requestBody) {
    try {
      log.info("APIM outbound path={} body={}", path, loggingSanitizer.toSafeString(requestBody));
    } catch (Exception ex) {
      log.warn("Failed to serialise APIM request body for logging.", ex);
    }
  }

  private static String mapUpstreamErrorCode(HttpStatusCode statusCode) {
    return switch (statusCode.value()) {
      case 503 -> ErrorCodes.APIM_SERVICE_UNAVAILABLE;
      case 504 -> ErrorCodes.APIM_TIMEOUT;
      default -> ErrorCodes.APIM_UPSTREAM_FAILURE;
    };
  }

  private static final class RetryContext {
    boolean tokenRetry = false;
    boolean certificateRetry = false;
    java.util.function.BiFunction<ApimCredentialProfile, java.security.PublicKey, Object>
        encryptionFunction = null;
    java.security.PublicKey responseVerificationKey = null;
  }

  private void logProfileResolutionFailure(
      ApimDatabaseScope scope,
      ApimCredentialResolutionContext ctx,
      String profileId,
      String reason) {
    try {
      Map<String, Object> event = new java.util.LinkedHashMap<>();
      event.put("event", "apim.profile.resolution.failure");
      event.put("operation", "resolve_profile");
      event.put("scope", scope == null ? null : scope.name());
      event.put(
          "actorRole",
          ctx == null || ctx.actorUserRole() == null ? null : ctx.actorUserRole().name());
      if (ctx != null && ctx.accountEnv() != null) {
        event.put("accountEnv", loggingSanitizer.sanitizeValue(ctx.accountEnv()));
      }
      if (profileId != null) {
        event.put("profileId", loggingSanitizer.sanitizeValue(profileId));
      }
      event.put("reason", loggingSanitizer.sanitizeText(reason));
      String payload = loggingSanitizer.toSafeString(event);
      log.error("{}", payload);
    } catch (Exception ex) {
      log.error("apim.profile.resolution.failure: failed to log structured event", ex);
    }
  }
}
