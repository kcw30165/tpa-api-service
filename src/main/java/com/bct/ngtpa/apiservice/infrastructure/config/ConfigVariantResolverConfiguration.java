package com.bct.ngtpa.apiservice.infrastructure.config;

import com.bct.ngtpa.apiservice.shared.config.ConfigVariantCandidateGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfigVariantResolverConfiguration {

    @Bean
    ConfigVariantCandidateGenerator configVariantCandidateGenerator() {
        return new ConfigVariantCandidateGenerator();
    }
}