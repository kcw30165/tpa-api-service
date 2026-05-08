package com.bct.ngtpa.apiservice.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimAppCertificateService;
import com.bct.ngtpa.apiservice.config.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.config.logging.LoggingSanitizerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.net.URI;
import java.security.Security;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class ConfigurationBeansTest {

    @Test
    void registersBouncyCastleProviderAndReusesExistingRegistration() {
        ApimCryptoConfig config = new ApimCryptoConfig();

        BouncyCastleProvider firstProvider = config.bouncyCastleProvider();
        BouncyCastleProvider secondProvider = config.bouncyCastleProvider();

        assertEquals(BouncyCastleProvider.PROVIDER_NAME, firstProvider.getName());
        assertEquals(firstProvider.getName(), secondProvider.getName());
        assertNotNull(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME));
    }

    @Test
    void createsObjectMapperBean() throws Exception {
        JacksonConfig config = new JacksonConfig();

        var objectMapper = config.objectMapper();
        String json = objectMapper.writeValueAsString(Map.of("message", "ok"));

        assertNotNull(objectMapper);
        assertTrue(objectMapper.canSerialize(Map.class));
        assertTrue(json.contains("\"message\":\"ok\""));
    }

    @Test
    void createsClientRegistrationWithoutScopesWhenNoneConfigured() {
        WebClientConfig config = new WebClientConfig(loggingSanitizer(), new ObjectMapper());
        ApimProperties properties = apimProperties(List.of());

        ReactiveClientRegistrationRepository repository = config.clientRegistrationRepository(properties);
        ClientRegistration registration = repository.findByRegistrationId("apim-client").block();

        assertNotNull(registration);
        assertEquals("client-id", registration.getClientId());
        assertEquals("client-secret", registration.getClientSecret());
        assertEquals("https://oauth.example.test/token", registration.getProviderDetails().getTokenUri());
        assertEquals("client_secret_post", registration.getClientAuthenticationMethod().getValue());
        assertEquals(null, registration.getScopes());
    }

    @Test
    void createsClientRegistrationWithConfiguredScopes() {
        WebClientConfig config = new WebClientConfig(loggingSanitizer(), new ObjectMapper());
        ApimProperties properties = apimProperties(List.of("scope.read", "scope.write"));

        ReactiveClientRegistrationRepository repository = config.clientRegistrationRepository(properties);
        ClientRegistration registration = repository.findByRegistrationId("apim-client").block();

        assertNotNull(registration);
        assertEquals(List.of("scope.read", "scope.write"), List.copyOf(registration.getScopes()));
    }

    @Test
    void createsAuthorizedClientBeansAndWebClients() {
        WebClientConfig config = new WebClientConfig(loggingSanitizer(), new ObjectMapper());
        ApimProperties properties = apimProperties(List.of("scope.read"));
        ReactiveClientRegistrationRepository repository = config.clientRegistrationRepository(properties);
        ReactiveOAuth2AuthorizedClientService clientService = config.authorizedClientService(repository);
        ReactiveOAuth2AuthorizedClientManager authorizedClientManager = config.authorizedClientManager(repository, clientService);
        ApimAppCertificateService certificateService = mock(ApimAppCertificateService.class);

        when(certificateService.getCertificateHeaderValue()).thenReturn("encoded-certificate");
        WebClient withCertificate = config.apimWebClient(properties, certificateService, authorizedClientManager);
        when(certificateService.getCertificateHeaderValue()).thenReturn(null);
        WebClient withoutCertificate = config.apimWebClient(properties, certificateService, authorizedClientManager);

        assertNotNull(clientService);
        assertNotNull(authorizedClientManager);
        assertNotNull(withCertificate);
        assertNotNull(withoutCertificate);
        assertNotNull(config.webClientBuilder());
    }

    @Test
    void requestLoggingFilterPassesRequestThrough() throws Exception {
        WebClientConfig config = new WebClientConfig(loggingSanitizer(), new ObjectMapper());
        ExchangeFilterFunction filter = logAndPropagateRequestId(config);
        ClientRequest request = ClientRequest.create(HttpMethod.POST, URI.create("https://api.example.test/notifications"))
                .header("Accept", "application/json")
                .header("Certificate", "encoded-certificate")
                .build();
        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();

        filter.filter(request, nextRequest -> {
            capturedRequest.set(nextRequest);
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        }).block();

        assertNotNull(capturedRequest.get());
        assertEquals(request.url(), capturedRequest.get().url());
        assertEquals(List.of("application/json"), capturedRequest.get().headers().get("Accept"));
        assertFalse(capturedRequest.get().headers().get("Certificate").isEmpty());
    }

    private static ApimProperties apimProperties(List<String> scopes) {
        ApimProperties properties = new ApimProperties();
        properties.setBaseUrl("https://api.example.test");
        properties.getOauth().setRegistrationId("apim-client");
        properties.getOauth().setClientId("client-id");
        properties.getOauth().setClientSecret("client-secret");
        properties.getOauth().setClientAuthenticationMethod("client_secret_post");
        properties.getOauth().setTokenUri("https://oauth.example.test/token");
        properties.getOauth().setScope(scopes);
        return properties;
    }

    private static ExchangeFilterFunction logAndPropagateRequestId(WebClientConfig config) throws Exception {
        Method method = WebClientConfig.class.getDeclaredMethod("logAndPropagateRequestId");
        method.setAccessible(true);
        return (ExchangeFilterFunction) method.invoke(config);
    }

    private static LoggingSanitizer loggingSanitizer() {
        return new LoggingSanitizer(new ObjectMapper(), new LoggingSanitizerProperties());
    }
}