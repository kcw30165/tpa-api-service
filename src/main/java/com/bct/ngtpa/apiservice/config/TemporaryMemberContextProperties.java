package com.bct.ngtpa.apiservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "temporary-member-context")
@Getter
@Setter
public class TemporaryMemberContextProperties {

    private Map<String, Profile> profiles = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class Profile {
        private String policyNo = "";
        private String certNo = "";
        private String userId = "";
        private String trustCode = "";
        private String schemeType = "";
    }
}
