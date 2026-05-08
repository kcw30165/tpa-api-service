package com.bct.ngtpa.apiservice.adapter.out.apim.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.bct.ngtpa.apiservice.config.ApimProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;

class ApimOAuthClientConfigTest {

    private final ApimOAuthClientConfig config = new ApimOAuthClientConfig();

    @Test
    void createsClientRegistrationWithoutScopesWhenNoneConfigured() {
        ApimProperties properties = apimProperties(List.of());

        ReactiveClientRegistrationRepository repository = config.clientRegistrationRepository(properties);
        ClientRegistration registration = repository.findByRegistrationId("apim-client").block();

        assertNotNull(registration);
        assertEquals("client-id", registration.getClientId());
        assertEquals("client-secret", registration.getClientSecret());
        assertEquals("https://oauth.example.test/token", registration.getProviderDetails().getTokenUri());
        assertEquals("client_secret_post", registration.getClientAuthenticationMethod().getValue());
        assertNull(registration.getScopes());
    }

    @Test
    void createsClientRegistrationWithConfiguredScopes() {
        ApimProperties properties = apimProperties(List.of("scope.read", "scope.write"));

        ReactiveClientRegistrationRepository repository = config.clientRegistrationRepository(properties);
        ClientRegistration registration = repository.findByRegistrationId("apim-client").block();

        assertNotNull(registration);
        assertEquals(List.of("scope.read", "scope.write"), List.copyOf(registration.getScopes()));
    }

    @Test
    void mapsClientIdSecretTokenUriAndAuthMethodFromProperties() {
        ApimProperties properties = apimProperties(List.of());

        ReactiveClientRegistrationRepository repository = config.clientRegistrationRepository(properties);
        ClientRegistration registration = repository.findByRegistrationId("apim-client").block();

        assertNotNull(registration);
        assertEquals("client-id", registration.getClientId());
        assertEquals("client-secret", registration.getClientSecret());
        assertEquals("https://oauth.example.test/token", registration.getProviderDetails().getTokenUri());
        assertEquals("client_secret_post", registration.getClientAuthenticationMethod().getValue());
        assertEquals("apim-client", registration.getRegistrationId());
    }

    @Test
    void createsAuthorizedClientServiceAndManagerBeans() {
        ApimProperties properties = apimProperties(List.of("scope.read"));

        ReactiveClientRegistrationRepository repository = config.clientRegistrationRepository(properties);
        ReactiveOAuth2AuthorizedClientService service = config.authorizedClientService(repository);
        ReactiveOAuth2AuthorizedClientManager manager = config.authorizedClientManager(repository, service);

        assertNotNull(service);
        assertNotNull(manager);
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
}
