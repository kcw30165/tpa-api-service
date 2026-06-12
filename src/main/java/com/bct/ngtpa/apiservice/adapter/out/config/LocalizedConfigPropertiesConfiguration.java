package com.bct.ngtpa.apiservice.adapter.out.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LocalizedConfigPropertiesConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "date-display-format")
    LocalizedConfigProperties dateFormatProperties() {
        return new LocalizedConfigProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "amount-display-format")
    LocalizedConfigProperties amountFormatProperties() {
        return new LocalizedConfigProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "currency-mapping")
    LocalizedConfigProperties currencyMappingProperties() {
        return new LocalizedConfigProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "error-message")
    LocalizedConfigProperties errorMessageProperties() {
        return new LocalizedConfigProperties();
    }
}
