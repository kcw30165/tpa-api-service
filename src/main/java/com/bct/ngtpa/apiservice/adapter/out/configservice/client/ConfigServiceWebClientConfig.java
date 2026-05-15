package com.bct.ngtpa.apiservice.adapter.out.configservice.client;

import com.bct.ngtpa.apiservice.adapter.out.configservice.config.ConfigServiceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Spring configuration for the Config Service outbound WebClient.
 * The produced bean is injected into {@code ConfigServiceWebClientAdapter}.
 */
@Configuration
@RequiredArgsConstructor
public class ConfigServiceWebClientConfig {

    @Bean
    public WebClient configServiceWebClient(ConfigServiceProperties configServiceProperties) {
        var builder = WebClient.builder()
                .baseUrl(configServiceProperties.getBaseUrl())
                .defaultHeader("Accept", "application/json");
        if (StringUtils.hasText(configServiceProperties.getUsername())) {
            builder.defaultHeaders(headers -> headers.setBasicAuth(
                    configServiceProperties.getUsername(),
                    configServiceProperties.getPassword()));
        }
        return builder.build();
    }
}
