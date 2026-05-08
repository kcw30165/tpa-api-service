package com.bct.ngtpa.apiservice.config;

import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimCryptoConfig;
import com.bct.ngtpa.apiservice.infrastructure.jackson.JacksonConfig;
import com.bct.ngtpa.apiservice.infrastructure.webclient.WebClientBaseConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.Security;
import java.util.Map;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

class ConfigurationBeansTest {

    @Test
    void registersBouncyCastleProviderAndReusesExistingRegistration() {
        ApimCryptoConfig config = new ApimCryptoConfig();

        BouncyCastleProvider firstProvider = config.bouncyCastleProvider();
        BouncyCastleProvider secondProvider = config.bouncyCastleProvider();

        assertEquals(BouncyCastleProvider.PROVIDER_NAME, firstProvider.getName());
        assertEquals(firstProvider.getName(), secondProvider.getName());
        assertNotNull(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME));
    }

    @Test
    void createsObjectMapperBean() throws Exception {
        JacksonConfig config = new JacksonConfig();

        var objectMapper = config.objectMapper();
        String json = objectMapper.writeValueAsString(Map.of("message", "ok"));

        assertNotNull(objectMapper);
        assertTrue(objectMapper.canSerialize(Map.class));
        assertTrue(json.contains("\"message\":\"ok\""));
    }

    @Test
    void createsWebClientBuilder() {
        WebClientBaseConfig config = new WebClientBaseConfig();

        assertNotNull(config.webClientBuilder());
    }
}