package com.bct.ngtpa.apiservice.adapter.out.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "temporary-portal-access-context")
@Getter
@Setter
public class TemporaryPortalAccessContextProperties {

    private Map<String, Profile> profiles = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class Profile {
        private String actorUserId = "";
        private String actorUserType = "";
        private String actorUserRole = "";
        private String memberUserId = "";
        private String memberType = "";
        private String accountEnv = "";
        private String policyNo = "";
        private String certNo = "";
        private String trustCode = "";
        private String schemeType = "";
        private String termStatus = "";
        private String termCompletionDate = "";
    }
}
