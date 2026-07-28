package com.bct.ngtpa.apiservice.config.auth.session;

import java.util.Base64;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth-workflows.login.session.protection")
@Getter
@Setter
public class AuthenticatedSessionProtectionProperties {
    private String keyVersion = "v1";
    private String base64Key;

    public void validate() {
        if (keyVersion == null || keyVersion.isBlank()) {
            throw new IllegalStateException(
                    "auth-workflows.login.session.protection.key-version must not be blank");
        }
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "auth-workflows.login.session.protection.base64-key must not be blank");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "auth-workflows.login.session.protection.base64-key must be valid Base64");
        }
        if (decoded.length != 32) {
            throw new IllegalStateException(
                    "auth-workflows.login.session.protection.base64-key must decode to 32 bytes");
        }
    }
}
