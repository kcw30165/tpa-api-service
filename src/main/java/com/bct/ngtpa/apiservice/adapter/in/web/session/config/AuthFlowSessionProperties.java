package com.bct.ngtpa.apiservice.adapter.in.web.session.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth-flow-session")
@Getter
@Setter
public class AuthFlowSessionProperties {

    private final SessionId sessionId = new SessionId();
    private final Cookie cookie = new Cookie();
    private final Csrf csrf = new Csrf();
    private final OriginValidation originValidation = new OriginValidation();
    private final Concurrency concurrency = new Concurrency();
    private final Scenarios scenarios = new Scenarios();

    @Getter
    @Setter
    public static class SessionId {
        private String prefix = "afs";
        private int entropyBytes = 16;
    }

    @Getter
    @Setter
    public static class Cookie {
        private String name = "auth_flow_sid";
        private String path = "/api/v1/auth";
        private int maxAgeSeconds = 1800;
        private boolean httpOnly = true;
        private boolean secure = true;
        private String sameSite = "None";
    }

    @Getter
    @Setter
    public static class Csrf {
        private String cookieName = "XSRF-TOKEN";
        private String headerName = "X-XSRF-TOKEN";
        private String cookiePath = "/api/v1/auth";
        private int maxAgeSeconds = 1800;
        private boolean secure = true;
        private String sameSite = "None";
    }

    @Getter
    @Setter
    public static class OriginValidation {
        private boolean enabled = true;
    }

    @Getter
    @Setter
    public static class Concurrency {
        private AuthFlowConcurrencyMode mode = AuthFlowConcurrencyMode.SINGLE_ACTIVE_FLOW;
        private AuthFlowOnNewFlowPolicy onNewFlow = AuthFlowOnNewFlowPolicy.REPLACE_EXISTING;
    }

    public enum AuthFlowConcurrencyMode {
        SINGLE_ACTIVE_FLOW,
        MULTI_FLOW
    }

    public enum AuthFlowOnNewFlowPolicy {
        REPLACE_EXISTING,
        ALLOW_PARALLEL
    }

    @Getter
    @Setter
    public static class Scenarios {
        private final Scenario registration = new Scenario(true, true, 1800);
        private final Scenario login = new Scenario(true, true, 900);
        private final Scenario forgotPassword = new Scenario(true, true, 1800);
        private final Scenario usernameRecovery = new Scenario(true, false, 900);
    }

    @Getter
    @Setter
    public static class Scenario {
        private boolean enabled;
        private boolean required;
        private int ttlSeconds;

        public Scenario() {
        }

        public Scenario(boolean enabled, boolean required, int ttlSeconds) {
            this.enabled = enabled;
            this.required = required;
            this.ttlSeconds = ttlSeconds;
        }
    }
}
