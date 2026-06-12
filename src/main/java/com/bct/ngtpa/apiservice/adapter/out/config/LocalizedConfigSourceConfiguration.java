package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.shared.config.ConfigCategory;
import com.bct.ngtpa.apiservice.shared.config.ConfigSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LocalizedConfigSourceConfiguration {

    @Bean
    ConfigSource displayDateFormatConfigSource(
            @Qualifier("dateFormatProperties") LocalizedConfigProperties properties) {
        return new LocalizedConfigSource(ConfigCategory.DISPLAY_DATE_FORMAT, properties, "date");
    }

    @Bean
    ConfigSource displayAmountFormatConfigSource(
            @Qualifier("amountFormatProperties") LocalizedConfigProperties properties) {
        return new LocalizedConfigSource(ConfigCategory.DISPLAY_AMOUNT_FORMAT, properties, "amount");
    }

    @Bean
    ConfigSource currencyMappingConfigSource(
            @Qualifier("currencyMappingProperties") LocalizedConfigProperties properties) {
        return new LocalizedConfigSource(ConfigCategory.CURRENCY_MAPPING, properties);
    }

    @Bean
    ConfigSource errorMessageConfigSource(
            @Qualifier("errorMessageProperties") LocalizedConfigProperties properties) {
        return new LocalizedConfigSource(ConfigCategory.ERROR_MESSAGE, properties);
    }
}
