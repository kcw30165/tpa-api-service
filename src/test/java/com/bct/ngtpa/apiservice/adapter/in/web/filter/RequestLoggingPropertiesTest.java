package com.bct.ngtpa.apiservice.adapter.in.web.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RequestLoggingPropertiesTest {

    @Test
    void exposesDefaultSafeLoggingConfiguration() {
        RequestLoggingProperties properties = new RequestLoggingProperties();

        assertTrue(properties.isEnabled());
        assertFalse(properties.isLogHeaders());
        assertFalse(properties.getBodyLogging().isEnabled());
        assertEquals(4096, properties.getBodyLogging().getDefaultMaxBodySizeBytes());
        assertTrue(properties.getBodyLogging().getEndpoints().isEmpty());
    }

    @Test
    void normalizesNullCollectionsAndNestedProperties() {
        RequestLoggingProperties properties = new RequestLoggingProperties();

        properties.setHeaderAllowlist(null);
        properties.setBodyLogging(null);

        assertTrue(properties.getHeaderAllowlist().isEmpty());
        assertFalse(properties.getBodyLogging().isEnabled());
    }

    @Test
    void storesEndpointBodyLoggingRules() {
        RequestLoggingProperties.BodyLogging body = new RequestLoggingProperties.BodyLogging();
        RequestLoggingProperties.EndpointRule endpoint =
                new RequestLoggingProperties.EndpointRule();
        endpoint.setMethod("PATCH");
        endpoint.setPathPattern("/api/v1/notifications");
        endpoint.setLogRequestBody(true);
        endpoint.setLogResponseBody(true);
        endpoint.setMaxBodySizeBytes(128);
        body.setEnabled(true);
        body.setEndpoints(List.of(endpoint));

        RequestLoggingProperties properties = new RequestLoggingProperties();
        properties.setBodyLogging(body);

        RequestLoggingProperties.EndpointRule actual = properties.getBodyLogging().getEndpoints().get(0);
        assertEquals("PATCH", actual.getMethod());
        assertEquals("/api/v1/notifications", actual.getPathPattern());
        assertTrue(actual.isLogRequestBody());
        assertTrue(actual.isLogResponseBody());
        assertEquals(128, actual.getMaxBodySizeBytes());
    }
}
