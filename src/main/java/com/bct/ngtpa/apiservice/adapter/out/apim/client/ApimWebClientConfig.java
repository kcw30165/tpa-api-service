package com.bct.ngtpa.apiservice.adapter.out.apim.client;

import com.bct.ngtpa.apiservice.adapter.out.apim.certificate.ApimCertificateHeaderProvider;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
@RequiredArgsConstructor
public class ApimWebClientConfig {

  private final ApimContextPropagationExchangeFilter contextPropagationExchangeFilter;
  private final ApimRequestLoggingExchangeFilter requestLoggingExchangeFilter;

  @Bean
  public WebClient apimWebClient(
      ApimProperties apimProperties,
      ApimCertificateHeaderProvider certificateHeaderProvider,
      ReactiveOAuth2AuthorizedClientManager authorizedClientManager) {

    ServerOAuth2AuthorizedClientExchangeFilterFunction oauth2Filter =
        new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);

    oauth2Filter.setDefaultClientRegistrationId(apimProperties.getOauth().getRegistrationId());

    HttpClient httpClient =
        HttpClient.create()
            .responseTimeout(Duration.ofMillis(apimProperties.validatedTimeoutMilliseconds()));

    WebClient.Builder webClientBuilder =
        WebClient.builder()
            .baseUrl(apimProperties.getBaseUrl())
            .defaultHeader("Accept", "application/json")
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .filter(oauth2Filter)
            .filter(contextPropagationExchangeFilter.filter())
            .filter(requestLoggingExchangeFilter.filter());

    String certHeader = certificateHeaderProvider.getCertificateHeaderValue();
    if (certHeader != null) {
      webClientBuilder.defaultHeader("Certificate", certHeader);
    }

    return webClientBuilder.build();
  }
}
