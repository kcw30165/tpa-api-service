package com.bct.ngtpa.apiservice.config;

import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApimCryptoConfig {

    @Bean
    public BouncyCastleProvider bouncyCastleProvider() {
        BouncyCastleProvider provider = new BouncyCastleProvider();
        if (Security.getProvider(provider.getName()) == null) {
            Security.addProvider(provider);
        }
        return provider;
    }
}