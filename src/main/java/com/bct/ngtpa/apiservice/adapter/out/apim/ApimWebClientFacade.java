package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimErrorResponse;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfile;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialProfileResolver;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.oauth.ApimTokenService;
import com.bct.ngtpa.apiservice.config.ApimProperties;
import com.bct.ngtpa.apiservice.shared.logging.LogExecution;
import com.bct.ngtpa.apiservice.config.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.config.logging.LoggingSanitizerProperties;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * APIM HTTP transport facade.
 * - Resolves credential profile per operation
 * - Attaches API key and Authorization header per-profile
 * - Evicts and retries on `invalid_token` and `invalid_public_key` when appropriate
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
            LoggingSanitizer loggingSanitizer) {
        this.apimWebClient = apimWebClient;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
        this.profileResolver = profileResolver;
        this.apimTokenService = apimTokenService;
        this.apimCertificateService = apimCertificateService;
        this.apimProperties = apimProperties;
        this.apimAppCertificateService = apimAppCertificateService;
        this.loggingSanitizer = loggingSanitizer;
    }

    public ApimWebClientFacade(
            WebClient apimWebClient,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            ApimCredentialProfileResolver profileResolver,
            ApimTokenService apimTokenService,
            ApimCertificateService apimCertificateService,
            ApimProperties apimProperties) {
        this(apimWebClient, webClientBuilder, objectMapper, profileResolver, apimTokenService,
                apimCertificateService, apimProperties, null,
                new LoggingSanitizer(objectMapper, new LoggingSanitizerProperties()));
    }

    public Mono<String> post(String path, Object requestBody) {
        ApimCredentialResolutionContext ctx = new ApimCredentialResolutionContext(
                null, null, null, null, null, null, null, path, Map.of());
        return post(path, requestBody, ctx);
    }

    @LogExecution(value = "apim.post", logArgs = true)
    public Mono<String> post(String path, Object requestBody, ApimCredentialResolutionContext resolutionContext) {
        return profileResolver.resolve(resolutionContext)
                .flatMap(profile -> sendWithProfile(profile, path, requestBody, new RetryContext()));
    }

    private Mono<String> sendWithProfile(ApimCredentialProfile profile, String path, Object requestBody, RetryContext retryContext) {
        return apimTokenService.getAccessToken(profile)
                .flatMap(token -> {
                    String baseUrl = profile.baseUrl() != null ? profile.baseUrl() : apimProperties.getBaseUrl();
                    WebClient client = webClientBuilder.clone().baseUrl(baseUrl).build();

                    WebClient.RequestBodySpec request = client.post()
                            .uri(uriBuilder -> uriBuilder.path(path).build())
                            .header(apimProperties.getApiKeyHeaderName(), profile.apiKey() != null ? profile.apiKey() : "")
                            .header("Authorization", "Bearer " + token);

                    String certificateHeaderValue = getCertificateHeaderValue();
                    if (apimProperties.getEncryption().isEnabled() && StringUtils.hasText(certificateHeaderValue)) {
                        request.header("Certificate", certificateHeaderValue);
                    }

                    WebClient.RequestHeadersSpec<?> req = request.bodyValue(requestBody);

                    logRequest(path, requestBody);
                    return req.exchangeToMono(response -> handleResponse(response, profile, path, requestBody, retryContext));
                });
    }

    protected String getCertificateHeaderValue() {
        return apimAppCertificateService == null ? null : apimAppCertificateService.getCertificateHeaderValue();
    }

    private Mono<String> handleResponse(ClientResponse response, ApimCredentialProfile profile, String path, Object requestBody, RetryContext retryContext) {
        HttpStatusCode statusCode = response.statusCode();
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> {
                    if (statusCode.isError()) {
                        String errorCode = extractApimErrorCode(body);
                        if ("invalid_token".equals(errorCode)) {
                            if (!retryContext.tokenRetry) {
                                retryContext.tokenRetry = true;
                                log.info("APIM returned invalid_token for profileId={}; evicting cached token and retrying once.",
                                        profile.profileId());
                                apimTokenService.evictToken(profile.profileId());
                                return apimTokenService.refreshAccessToken(profile)
                                        .flatMap(e -> sendWithProfile(profile, path, requestBody, retryContext));
                            }
                        }
                        if ("invalid_public_key".equals(errorCode)) {
                            if (!retryContext.certificateRetry) {
                                retryContext.certificateRetry = true;
                                                        apimCertificateService.evictCertificate(profile.profileId());
                                                        // Let caller (adapter) be responsible for re-encryption; retry the same payload once
                                                        return sendWithProfile(profile, path, requestBody, retryContext);
                            }
                        }

                        log.error("APIM request failed with status={} body={}", statusCode.value(), body);
                        return Mono.error(new ApimException(HttpStatus.BAD_GATEWAY, "APIM request failed with status=%s body=%s".formatted(statusCode.value(), body)));
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

    private static final class RetryContext {
        boolean tokenRetry = false;
        boolean certificateRetry = false;
    }

    // Legacy compatibility constructor used by tests and simple subclasses.
    public ApimWebClientFacade(WebClient apimWebClient, ObjectMapper objectMapper) {
        this.apimWebClient = apimWebClient;
        this.webClientBuilder = WebClient.builder();
        this.objectMapper = objectMapper;
        this.profileResolver = ctx -> Mono.empty();
        this.apimTokenService = new com.bct.ngtpa.apiservice.adapter.out.apim.oauth.ApimTokenService(WebClient.builder(), new ApimProperties());
        this.apimCertificateService = new ApimCertificateService(WebClient.builder(), new ApimProperties(), null);
        this.apimProperties = new ApimProperties();
        this.apimAppCertificateService = null;
        this.loggingSanitizer = new LoggingSanitizer(objectMapper, new LoggingSanitizerProperties());
    }
}
