package com.bct.ngtpa.apiservice.adapter.out.apim.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.apim.certificate.ApimCertificateHeaderProvider;
import com.bct.ngtpa.apiservice.adapter.out.apim.oauth.ApimOAuthClientConfig;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.web.reactive.function.client.WebClient;

class ApimWebClientConfigTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LoggingSanitizer loggingSanitizer =
            new LoggingSanitizer(objectMapper, new LoggingSanitizerProperties());

    private final ApimRequestIdExchangeFilter requestIdFilter = new ApimRequestIdExchangeFilter();
    private final ApimRequestLoggingExchangeFilter requestLoggingFilter =
            new ApimRequestLoggingExchangeFilter(loggingSanitizer, objectMapper);
    private final ApimWebClientConfig config = new ApimWebClientConfig(requestIdFilter, requestLoggingFilter);

    @Test
    void createsApimWebClientWithCertificateHeader() {
        ApimCertificateHeaderProvider certProvider = mock(ApimCertificateHeaderProvider.class);
        when(certProvider.getCertificateHeaderValue()).thenReturn("encoded-certificate");

        WebClient webClient = config.apimWebClient(
                apimProperties(),
                certProvider,
                authorizedClientManager()
        );

        assertNotNull(webClient);
    }

    @Test
    void createsApimWebClientWithoutCertificateHeader() {
        ApimCertificateHeaderProvider certProvider = mock(ApimCertificateHeaderProvider.class);
        when(certProvider.getCertificateHeaderValue()).thenReturn(null);

        WebClient webClient = config.apimWebClient(
                apimProperties(),
                certProvider,
                authorizedClientManager()
        );

        assertNotNull(webClient);
    }

    private static ApimProperties apimProperties() {
        ApimProperties properties = new ApimProperties();
        properties.setBaseUrl("https://api.example.test");
        properties.getOauth().setRegistrationId("apim-client");
        properties.getOauth().setClientId("client-id");
        properties.getOauth().setClientSecret("client-secret");
        properties.getOauth().setClientAuthenticationMethod("client_secret_post");
        properties.getOauth().setTokenUri("https://oauth.example.test/token");
        properties.getOauth().setScope(List.of("scope.read"));
        return properties;
    }

    private static ReactiveOAuth2AuthorizedClientManager authorizedClientManager() {
        ApimOAuthClientConfig oauthConfig = new ApimOAuthClientConfig();
        ApimProperties props = apimProperties();
        ReactiveClientRegistrationRepository repository = oauthConfig.clientRegistrationRepository(props);
        ReactiveOAuth2AuthorizedClientService service = oauthConfig.authorizedClientService(repository);
        return oauthConfig.authorizedClientManager(repository, service);
    }
}
