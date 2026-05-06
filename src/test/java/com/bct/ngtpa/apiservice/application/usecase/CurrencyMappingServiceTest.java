package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.config.CurrencyMappingProperties;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CurrencyMappingServiceTest {

    @Test
    void resolvesUsingFallbackOrderAndFallsBackToRawTrimmedCode() {
        var properties = new CurrencyMappingProperties();
        Map<String, String> english = new LinkedHashMap<>();
        english.put("HKD", "HKD");
        english.put("HKD.JP", "HKD JP");
        english.put("HKD.JP.TRUST", "HKD TRUST");
        english.put("HKD.JP.TRUST.SCHEME", "HKD SCHEME");
        properties.put("en", english);

        var service = new CurrencyMappingService(properties);

        assertEquals("HKD SCHEME", service.resolve("en", "HKD", "JP", "TRUST", "SCHEME"));
        assertEquals("HKD TRUST", service.resolve("en", "HKD", "JP", "TRUST", ""));
        assertEquals("HKD JP", service.resolve("en", "HKD", "JP", "", ""));
        assertEquals("HKD", service.resolve("en", "HKD", "", "", ""));
        assertEquals("USD", service.resolve("en", " USD ", "JP", "", ""));
        assertEquals("", service.resolve("en", "   ", "JP", "", ""));
    }
}