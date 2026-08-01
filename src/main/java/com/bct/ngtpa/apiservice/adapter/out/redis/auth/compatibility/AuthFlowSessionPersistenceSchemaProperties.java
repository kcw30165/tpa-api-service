package com.bct.ngtpa.apiservice.adapter.out.redis.auth.compatibility;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth-flow-session")
public class AuthFlowSessionPersistenceSchemaProperties {
    private Integer persistenceSchemaVersion;

    public Integer getPersistenceSchemaVersion() { return persistenceSchemaVersion; }
    public void setPersistenceSchemaVersion(Integer value) { persistenceSchemaVersion = value; }

    public int requiredVersion() {
        if (persistenceSchemaVersion == null) {
            throw new IllegalStateException("auth-flow-session.persistence-schema-version is required");
        }
        if (persistenceSchemaVersion != 2) {
            throw new IllegalStateException(
                    "auth-flow-session.persistence-schema-version expectedVersion=2 actualVersion="
                            + persistenceSchemaVersion);
        }
        return persistenceSchemaVersion;
    }
}
