package com.bct.ngtpa.apiservice.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "apim")
@Getter
@Setter
public class ApimProperties {
    private String baseUrl;
    private int timeoutMilliseconds;
    private final Encryption encryption = new Encryption();

    @Getter
    @Setter
    public static class Encryption {
        private boolean enabled;
        private String certificatePath = "/api/wssupport/v1/encryption/certificate";
        private String apiKey;
        private String privateKeyPem;
        private final Map<String, ApiFieldEncryptionConfig> apis = new LinkedHashMap<>();
    }

    @Getter
    @Setter
    public static class ApiFieldEncryptionConfig {
        private List<String> requestFields = new ArrayList<>();
        private List<String> responseFields = new ArrayList<>();
    }
}
