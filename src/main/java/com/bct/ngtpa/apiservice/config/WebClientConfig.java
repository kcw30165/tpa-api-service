package com.bct.ngtpa.apiservice.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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
            OAuth2AuthorizedClientManager authorizedClientManager) {

        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Filter = new ServletOAuth2AuthorizedClientExchangeFilterFunction(
                authorizedClientManager);

        oauth2Filter.setDefaultClientRegistrationId("apim-client");

        return WebClient.builder()
                .baseUrl(apimProperties.getBaseUrl())
                .defaultHeader("Accept", "application/json")
                .apply(oauth2Filter.oauth2Configuration())
                .filter(stripBearerPrefixInAuthorizationHeader())
                .filter(logApimRequestHeaders())
                .build();
    }

    private ExchangeFilterFunction stripBearerPrefixInAuthorizationHeader() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            List<String> authorizationHeaders = request.headers().getOrEmpty(HttpHeaders.AUTHORIZATION);
            if (authorizationHeaders.isEmpty()) {
                return Mono.just(request);
            }

            String authorizationValue = authorizationHeaders.get(0);
            if (authorizationValue == null || !authorizationValue.startsWith("Bearer ")) {
                return Mono.just(request);
            }

            String tokenOnly = authorizationValue.substring("Bearer ".length());
            ClientRequest mutatedRequest = ClientRequest.from(request)
                    .headers(headers -> headers.set(HttpHeaders.AUTHORIZATION, tokenOnly))
                    .build();
            return Mono.just(mutatedRequest);
        });
    }

    private ExchangeFilterFunction logApimRequestHeaders() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            Map<String, List<String>> sanitizedHeaders = new LinkedHashMap<>();
            request.headers().forEach((name, values) -> {
                if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                    String firstValue = values.isEmpty() ? null : values.get(0);
                    sanitizedHeaders.put(name, List.of(maskAuthorization(firstValue)));
                } else {
                    sanitizedHeaders.put(name, values);
                }
            });

            logger.info("APIM outbound request method={} url={} headers={}",
                    request.method(), request.url(), sanitizedHeaders);
            return Mono.just(request);
        });
    }

    private String maskAuthorization(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return "MISSING";
        }
        if (authorizationHeader.startsWith("Basic ")) {
            return "Basic ***";
        }
        String token = authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring("Bearer ".length())
                : authorizationHeader;
        if (token.length() <= 10) {
            return "***";
        }
        return token.substring(0, 6) + "...";
    }
}