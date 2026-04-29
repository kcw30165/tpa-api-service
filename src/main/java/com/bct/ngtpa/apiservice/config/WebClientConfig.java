package com.bct.ngtpa.apiservice.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimAppCertificateService;

@Configuration
public class WebClientConfig {
    private static final Logger logger = LoggerFactory.getLogger(WebClientConfig.class);

    @Bean
    public ReactiveClientRegistrationRepository clientRegistrationRepository(ApimProperties apimProperties) {
        ApimProperties.Oauth oauth = apimProperties.getOauth();

        ClientRegistration.Builder builder = ClientRegistration
                .withRegistrationId(oauth.getRegistrationId())
                .clientId(oauth.getClientId())
                .clientSecret(oauth.getClientSecret())
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientAuthenticationMethod(new ClientAuthenticationMethod(oauth.getClientAuthenticationMethod()))
                .tokenUri(oauth.getTokenUri());

        if (!CollectionUtils.isEmpty(oauth.getScope())) {
            builder.scope(oauth.getScope());
        }

        return new InMemoryReactiveClientRegistrationRepository(builder.build());
    }

    @Bean
    public ReactiveOAuth2AuthorizedClientService authorizedClientService(
            ReactiveClientRegistrationRepository clientRegistrationRepository) {
        return new InMemoryReactiveOAuth2AuthorizedClientService(clientRegistrationRepository);
    }

    @Bean
    public ReactiveOAuth2AuthorizedClientManager authorizedClientManager(
            ReactiveClientRegistrationRepository clientRegistrationRepository,
            ReactiveOAuth2AuthorizedClientService authorizedClientService) {
        ReactiveOAuth2AuthorizedClientProvider authorizedClientProvider = ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build();

        AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager authorizedClientManager = new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
                clientRegistrationRepository, authorizedClientService);
        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);
        return authorizedClientManager;
    }

    @Bean
    public WebClient apimWebClient(ApimProperties apimProperties,
            ApimAppCertificateService apimAppCertificateService,
            ReactiveOAuth2AuthorizedClientManager authorizedClientManager) {

        ServerOAuth2AuthorizedClientExchangeFilterFunction oauth2Filter = new ServerOAuth2AuthorizedClientExchangeFilterFunction(
                authorizedClientManager);

        oauth2Filter.setDefaultClientRegistrationId(apimProperties.getOauth().getRegistrationId());

        WebClient.Builder webClientBuilder = WebClient.builder()
                .baseUrl(apimProperties.getBaseUrl())
                .defaultHeader("Accept", "application/json")
            .filter(oauth2Filter)
                .filter(logApimRequestHeaders());

        String certHeader = apimAppCertificateService.getCertificateHeaderValue();
        if (certHeader != null) {
            webClientBuilder.defaultHeader("Certificate", certHeader);
        }

        return webClientBuilder.build();
    }


    private ExchangeFilterFunction logApimRequestHeaders() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            Map<String, List<String>> headers = new LinkedHashMap<>();
            request.headers().forEach((name, values) -> {
                headers.put(name, values);
            });

            logger.info("APIM outbound request method={} url={} headers={}",
                    request.method(), request.url(), headers);
            return Mono.just(request);
        });
    }
}
