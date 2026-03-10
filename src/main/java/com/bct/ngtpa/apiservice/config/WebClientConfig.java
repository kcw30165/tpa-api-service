package com.bct.ngtpa.apiservice.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import com.bct.ngtpa.apiservice.service.ApimAppCertificateService;

@Configuration
public class WebClientConfig {
    private static final Logger logger = LoggerFactory.getLogger(WebClientConfig.class);

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {
        OAuth2AuthorizedClientProvider authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build();

        AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientManager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                clientRegistrationRepository, authorizedClientService);
        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);
        return authorizedClientManager;
    }

    @Bean
    public WebClient apimWebClient(ApimProperties apimProperties,
            ApimAppCertificateService apimAppCertificateService,
            OAuth2AuthorizedClientManager authorizedClientManager) {

        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Filter = new ServletOAuth2AuthorizedClientExchangeFilterFunction(
                authorizedClientManager);

        oauth2Filter.setDefaultClientRegistrationId("apim-client");

        WebClient.Builder webClientBuilder = WebClient.builder()
                .baseUrl(apimProperties.getBaseUrl())
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Certificate", apimAppCertificateService.getCertificateHeaderValue())
                .apply(oauth2Filter.oauth2Configuration())
                .filter(logApimRequestHeaders());

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