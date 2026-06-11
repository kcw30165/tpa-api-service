package com.bct.ngtpa.apiservice.adapter.out.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorMessagePropertiesTest {

    @Test
    void findReturnsConfiguredMessage() {
        var properties = new ErrorMessageProperties();
        properties.put("en", Map.of("err.request.invalid", "Invalid request."));

        assertThat(properties.find("en", "err.request.invalid"))
                .contains("Invalid request.");
    }

    @Test
    void findTrimsLookupInputs() {
        var properties = new ErrorMessageProperties();
        properties.put("en", Map.of("err.request.invalid", "Invalid request."));

        assertThat(properties.find(" en ", " err.request.invalid "))
                .contains("Invalid request.");
    }

    @Test
    void findReturnsEmptyForBlankLocale() {
        var properties = new ErrorMessageProperties();
        properties.put("en", Map.of("err.request.invalid", "Invalid request."));

        assertThat(properties.find(" ", "err.request.invalid"))
                .isEmpty();
    }

    @Test
    void findReturnsEmptyForBlankKey() {
        var properties = new ErrorMessageProperties();
        properties.put("en", Map.of("err.request.invalid", "Invalid request."));

        assertThat(properties.find("en", " "))
                .isEmpty();
    }

    @Test
    void findReturnsEmptyForMissingLocale() {
        var properties = new ErrorMessageProperties();
        properties.put("en", Map.of("err.request.invalid", "Invalid request."));

        assertThat(properties.find("zh_HK", "err.request.invalid"))
                .isEmpty();
    }

    @Test
    void findReturnsEmptyForMissingKey() {
        var properties = new ErrorMessageProperties();
        properties.put("en", new LinkedHashMap<>());

        assertThat(properties.find("en", "err.request.invalid"))
                .isEmpty();
    }
}
