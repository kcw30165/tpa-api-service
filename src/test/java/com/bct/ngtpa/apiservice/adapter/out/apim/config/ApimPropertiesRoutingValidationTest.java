package com.bct.ngtpa.apiservice.adapter.out.apim.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApimPropertiesRoutingValidationTest {
    @Test
    void missingNonAccountProfileIdFailsValidation() {
        ApimProperties props = new ApimProperties();
        // no routing configured
        assertThatThrownBy(props::validateRouting)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-account-profile-id");
    }
}
