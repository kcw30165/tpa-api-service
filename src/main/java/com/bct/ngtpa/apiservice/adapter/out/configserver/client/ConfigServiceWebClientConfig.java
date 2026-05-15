package com.bct.ngtpa.apiservice.adapter.out.configserver.client;

import com.bct.ngtpa.apiservice.adapter.out.configserver.config.ConfigServiceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Spring configuration for the Config Service outbound WebClient.
 * The produced bean is injected into {@code ConfigServiceWebClientAdapter}.
 */
@Configuration
@RequiredArgsConstructor
public class ConfigServiceWebClientConfig {

    @Bean
    public WebClient configServiceWebClient(ConfigServiceProperties configServiceProperties) {
        return WebClient.builder()
                .baseUrl(configServiceProperties.getBaseUrl())
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
