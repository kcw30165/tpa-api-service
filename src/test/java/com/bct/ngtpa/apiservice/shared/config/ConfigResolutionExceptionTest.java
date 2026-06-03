package com.bct.ngtpa.apiservice.shared.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class ConfigResolutionExceptionTest {

    @Test
    void preservesMessage() {
        ConfigResolutionException exception = new ConfigResolutionException("missing config");

        assertEquals("missing config", exception.getMessage());
    }

    @Test
    void preservesMessageAndCause() {
        RuntimeException cause = new RuntimeException("root cause");
        ConfigResolutionException exception = new ConfigResolutionException("failed", cause);

        assertEquals("failed", exception.getMessage());
        assertSame(cause, exception.getCause());
    }
}
