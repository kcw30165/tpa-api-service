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

    /** Transitional legacy shape retained until callers move to the request-scoped session resolver. */
    private Map<String, Profile> profiles = new LinkedHashMap<>();

    /** Temporary Redis-like shape: session-id -> actor + authorized accounts. */
    private Map<String, SessionProfile> sessions = new LinkedHashMap<>();

    /** Temporary stand-in for the HttpOnly session-id cookie until login/session exists. */
    private String defaultSessionId = "";

    @Getter
    @Setter
    public static class SessionProfile {
        private SessionActor actor = new SessionActor();
        private Map<String, AccountProfile> accounts = new LinkedHashMap<>();
    }

    @Getter
    @Setter
    public static class SessionActor {
        private String actorUserId = "";
        private String actorUserRole = "";
    }

    @Getter
    @Setter
    public static class AccountProfile {
        private String accountEnv = "";
        private String policyNo = "";
        private String certNo = "";
        private String trustCode = "";
        private String schemeType = "";
        private String termStatus = "";
        private String termCompletionDate = "";
    }

    @Getter
    @Setter
    public static class Profile {
        private String actorUserId = "";
        private String actorUserType = "";
        private String actorUserRole = "";
        private String accountEnv = "";
        private String policyNo = "";
        private String certNo = "";
        private String trustCode = "";
        private String schemeType = "";
        private String termStatus = "";
        private String termCompletionDate = "";
    }
}
