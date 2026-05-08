package com.bct.ngtpa.apiservice.adapter.out.apim.client;

import com.bct.ngtpa.apiservice.adapter.out.apim.certificate.ApimCertificateHeaderProvider;
import com.bct.ngtpa.apiservice.config.ApimProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class ApimWebClientConfig {

    private final ApimRequestIdExchangeFilter requestIdExchangeFilter;
    private final ApimRequestLoggingExchangeFilter requestLoggingExchangeFilter;

    @Bean
    public WebClient apimWebClient(
            ApimProperties apimProperties,
            ApimCertificateHeaderProvider certificateHeaderProvider,
            ReactiveOAuth2AuthorizedClientManager authorizedClientManager) {

        ServerOAuth2AuthorizedClientExchangeFilterFunction oauth2Filter =
                new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);

        oauth2Filter.setDefaultClientRegistrationId(
                apimProperties.getOauth().getRegistrationId()
        );

        WebClient.Builder webClientBuilder = WebClient.builder()
                .baseUrl(apimProperties.getBaseUrl())
                .defaultHeader("Accept", "application/json")
                .filter(oauth2Filter)
                .filter(requestIdExchangeFilter.filter())
                .filter(requestLoggingExchangeFilter.filter());

        String certHeader = certificateHeaderProvider.getCertificateHeaderValue();
        if (certHeader != null) {
            webClientBuilder.defaultHeader("Certificate", certHeader);
        }

        return webClientBuilder.build();
    }
}
