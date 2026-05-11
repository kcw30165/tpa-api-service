package com.bct.ngtpa.apiservice.adapter.out.apim.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApimPropertiesBindingTest {

    @Test
    void bindsEncryptionRequestFields() {
        Map<String, String> props = Map.of(
                "apim.encryption.enabled", "true",
                "apim.encryption.request-fields[0]", "policy-no",
                "apim.encryption.request-fields[1]", "cert-no",
                "apim.encryption.request-fields[2]", "user-id"
        );

        Binder binder = new Binder(new MapConfigurationPropertySource(props));
        ApimProperties bound = binder.bind("apim", Bindable.of(ApimProperties.class))
            .orElseThrow(() -> new IllegalStateException("Failed to bind ApimProperties"));

        assertTrue(bound.getEncryption().isEnabled());
        assertEquals(List.of("policy-no", "cert-no", "user-id"), bound.getEncryption().getRequestFields());
    }
}
