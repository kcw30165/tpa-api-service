package com.bct.ngtpa.apiservice.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@ConfigurationProperties(prefix = "apim")
@Getter
@Setter
public class ApimProperties {
    private String baseUrl;
    private int timeoutMilliseconds;
    private String apiKeyHeaderName = "Ocp-Apim-Subscription-Key";
    private final Oauth oauth = new Oauth();
    private final Encryption encryption = new Encryption();
    private final CredentialProfiles credentialProfiles = new CredentialProfiles();

    @Getter
    @Setter
    public static class Oauth {
        private String registrationId = "apim-client";
        private String clientId = "local-dev";
        private String clientSecret = "local-dev";
        private String clientAuthenticationMethod = "client_secret_basic";
        private String tokenUri = "http://localhost/token";
        private List<String> scope = new ArrayList<>();
        private int tokenRenewalSkewSeconds = 60;
        private String renewalStrategy = "client-credentials";
    }

    @Getter
    @Setter
    public static class Encryption {
        private boolean enabled;
        private String certificatePath = "/api/wssupport/v1/encryption/certificate";
        private String apiKey;
        private String privateKeyPem;
        private String publicKeyPem;
        private List<String> requestFields = new ArrayList<>();
        private List<String> responseFields = new ArrayList<>();
        private int certificateCacheTtlSeconds = 3600;
        private int certificateRenewalSkewSeconds = 60;
    }

    @Getter
    @Setter
    public static class CredentialProfiles {
        private String defaultProfileId = "default";
        private List<Profile> profiles = new ArrayList<>();

        @Getter
        @Setter
        public static class Profile {
            private String profileId = "default";
            private String baseUrl;
            private String tokenUri;
            private String clientId;
            private String clientSecret;
            private String apiKey;
            private List<String> scope = new ArrayList<>();
            private String certificatePath = "/api/wssupport/v1/encryption/certificate";
        }
    }
    
}
