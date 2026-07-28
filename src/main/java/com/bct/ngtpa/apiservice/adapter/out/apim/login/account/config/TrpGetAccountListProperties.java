package com.bct.ngtpa.apiservice.adapter.out.apim.login.account.config;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "apim.login.get-account-list")
public class TrpGetAccountListProperties {
    private String path = "/ws/NGTPA/v1/TRPGetAccountList";
    private String operationName = "TRPGetAccountList";
    private Map<String, String> errorMappings = new LinkedHashMap<>();

    public void validate() {
        if (path == null || path.isBlank())
            throw new IllegalStateException("apim.login.get-account-list.path must not be blank");
        if (operationName == null || operationName.isBlank())
            throw new IllegalStateException("apim.login.get-account-list.operation-name must not be blank");
        if (errorMappings == null || errorMappings.isEmpty())
            throw new IllegalStateException("apim.login.get-account-list.error-mappings must not be empty");
        errorMappings.forEach((code, outcome) -> {
            if (code == null || code.isBlank() || !"UNAVAILABLE".equals(outcome))
                throw new IllegalStateException("Invalid TRPGetAccountList error mapping");
        });
    }
}
