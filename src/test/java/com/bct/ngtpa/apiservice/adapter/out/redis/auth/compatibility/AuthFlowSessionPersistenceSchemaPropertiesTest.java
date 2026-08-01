package com.bct.ngtpa.apiservice.adapter.out.redis.auth.compatibility;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AuthFlowSessionPersistenceSchemaPropertiesTest {
    @Test void versionTwoIsRequired() {
        var properties = new AuthFlowSessionPersistenceSchemaProperties();
        properties.setPersistenceSchemaVersion(2);
        assertThat(properties.requiredVersion()).isEqualTo(2);
    }

    @Test void missingAndUnsupportedVersionsFailClosed() {
        var properties = new AuthFlowSessionPersistenceSchemaProperties();
        assertThatThrownBy(properties::requiredVersion).isInstanceOf(IllegalStateException.class);
        for (int value : new int[]{-1, 0, 1, 3}) {
            properties.setPersistenceSchemaVersion(value);
            assertThatThrownBy(properties::requiredVersion)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("expectedVersion=2");
        }
    }
}
