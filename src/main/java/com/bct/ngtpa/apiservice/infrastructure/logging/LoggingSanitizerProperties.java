package com.bct.ngtpa.apiservice.infrastructure.logging;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "logging-sanitizer")
public class LoggingSanitizerProperties {

    private List<String> sensitiveTokens = new ArrayList<>(List.of(
            "password",
            "clientSecret",
            "client_secret",
            "clientId",
            "client_id",
            "apiKey",
            "apikey",
            "token",
            "secret",
            "policyNo",
            "policy_no",
            "policy-no",
            "certificate",
            "cert"
    ));

    public List<String> getSensitiveTokens() {
        return sensitiveTokens;
    }

    public void setSensitiveTokens(List<String> sensitiveTokens) {
        this.sensitiveTokens = sensitiveTokens == null ? new ArrayList<>() : new ArrayList<>(sensitiveTokens);
    }
}
