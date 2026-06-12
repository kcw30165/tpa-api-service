package com.bct.ngtpa.apiservice.infrastructure.config;

import com.bct.ngtpa.apiservice.adapter.out.config.DefaultConfigKeyCandidateStrategy;
import com.bct.ngtpa.apiservice.shared.config.ConfigCategory;
import com.bct.ngtpa.apiservice.shared.config.ConfigKeyCandidateStrategy;
import com.bct.ngtpa.apiservice.shared.config.ConfigVariantCandidateGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfigVariantResolverConfiguration {

    @Bean
    ConfigVariantCandidateGenerator configVariantCandidateGenerator() {
        return new ConfigVariantCandidateGenerator();
    }

    @Bean
    ConfigKeyCandidateStrategy errorMessageKeyCandidateStrategy(
            ConfigVariantCandidateGenerator configVariantCandidateGenerator) {
        return new DefaultConfigKeyCandidateStrategy(
                configVariantCandidateGenerator,
                ConfigCategory.ERROR_MESSAGE);
    }

    @Bean
    ConfigKeyCandidateStrategy currencyMappingKeyCandidateStrategy(
            ConfigVariantCandidateGenerator configVariantCandidateGenerator) {
        return new DefaultConfigKeyCandidateStrategy(
                configVariantCandidateGenerator,
                ConfigCategory.CURRENCY_MAPPING);
    }

    @Bean
    ConfigKeyCandidateStrategy displayFormatKeyCandidateStrategy(
            ConfigVariantCandidateGenerator configVariantCandidateGenerator) {
        return new DefaultConfigKeyCandidateStrategy(
                configVariantCandidateGenerator,
                ConfigCategory.DISPLAY_FORMAT);
    }
}
