package com.bct.ngtpa.apiservice.adapter.out.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.DownstreamErrorClassificationMappingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.DownstreamErrorClassificationProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageSchemaProperties;
import com.bct.ngtpa.apiservice.application.dto.downstream.DownstreamErrorClassificationRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DownstreamErrorClassificationAdapterTest {

    @Test
    void explicitMappingWins() {
        var adapter = new ConfigBackedDownstreamErrorClassificationAdapter(properties());

        var result = adapter.classify(request(
                "registration-username-check",
                "APIM_TAS",
                "TRPChkCustId",
                "err.tpa.username.taken"));

        assertEquals("registration.username.isregistered", result.responseCode());
        assertEquals("PAGE", result.target());
        assertFalse(result.fallbackUsed());
    }

    @Test
    void unknownDownstreamCodeUsesPageFallback() {
        var adapter = new ConfigBackedDownstreamErrorClassificationAdapter(properties());

        var result = adapter.classify(request(
                "registration-username-check",
                "APIM_TAS",
                "TRPChkCustId",
                "err.tpa.some.unknown.business.error"));

        assertEquals("downstream.generic.businessRejected", result.responseCode());
        assertEquals("PAGE", result.target());
        assertTrue(result.fallbackUsed());
    }

    @Test
    void registrationCompleteFallbackDiffersByPage() {
        var adapter = new ConfigBackedDownstreamErrorClassificationAdapter(properties());

        var result = adapter.classify(request(
                "registration-password-setup",
                "APIM_TAS",
                "TRPRegMember",
                "err.tpa.unknown"));

        assertEquals("registration.complete.failed", result.responseCode());
        assertEquals("PAGE", result.target());
        assertTrue(result.fallbackUsed());
    }

    @Test
    void wrongEndpointDoesNotUseAnotherEndpointExplicitMapping() {
        var adapter = new ConfigBackedDownstreamErrorClassificationAdapter(properties());

        var result = adapter.classify(request(
                "registration-username-check",
                "APIM_TAS",
                "TRPRegMember",
                "err.tpa.username.taken"));

        assertEquals("downstream.generic.businessRejected", result.responseCode());
        assertEquals("PAGE", result.target());
        assertTrue(result.fallbackUsed());
    }

    @Test
    void missingPageConfigUsesBuiltInGenericFallback() {
        var adapter = new ConfigBackedDownstreamErrorClassificationAdapter(properties());

        var result = adapter.classify(request(
                "unknown-page",
                "APIM_TAS",
                "TRPChkCustId",
                "err.tpa.username.taken"));

        assertEquals("DOWNSTREAM_REJECTION", result.eventType());
        assertEquals("ERROR", result.logLevel());
        assertEquals("downstream.generic.businessRejected", result.responseCode());
        assertEquals("PAGE", result.target());
        assertFalse(result.idempotencyCacheable());
        assertNull(result.cacheTtlSeconds());
    }

    @Test
    void cacheTtlOnlyPresentWhenConfigured() {
        var adapter = new ConfigBackedDownstreamErrorClassificationAdapter(properties());

        var explicit = adapter.classify(request(
                "registration-username-check",
                "APIM_TAS",
                "TRPChkCustId",
                "err.tpa.username.taken"));
        var fallback = adapter.classify(request(
                "registration-username-check",
                "APIM_TAS",
                "TRPChkCustId",
                "err.tpa.unknown"));

        assertTrue(explicit.idempotencyCacheable());
        assertEquals(60, explicit.cacheTtlSeconds());
        assertFalse(fallback.idempotencyCacheable());
        assertNull(fallback.cacheTtlSeconds());
    }

    private DownstreamErrorClassificationRequest request(
            String pageId,
            String source,
            String endpoint,
            String downstreamErrorCode) {
        return new DownstreamErrorClassificationRequest(pageId, source, endpoint, downstreamErrorCode);
    }

    private BffPagesProperties properties() {
        var properties = new BffPagesProperties();
        Map<String, PageSchemaProperties> pages = new LinkedHashMap<>();
        pages.put("registration-username-check", page(usernameCheckClassification()));
        pages.put("registration-password-setup", page(passwordSetupClassification()));
        properties.setPages(pages);
        return properties;
    }

    private PageSchemaProperties page(DownstreamErrorClassificationProperties classification) {
        var page = new PageSchemaProperties();
        page.setDownstreamErrorClassification(classification);
        return page;
    }

    private DownstreamErrorClassificationProperties usernameCheckClassification() {
        var classification = new DownstreamErrorClassificationProperties();
        classification.setSource("APIM_TAS");
        classification.setEndpoint("TRPChkCustId");
        classification.setMappings(Map.of("err.tpa.username.taken", usernameTakenMapping()));
        classification.setFallback(mapping("downstream.generic.businessRejected", false, null));
        return classification;
    }

    private DownstreamErrorClassificationProperties passwordSetupClassification() {
        var classification = new DownstreamErrorClassificationProperties();
        classification.setSource("APIM_TAS");
        classification.setEndpoint("TRPRegMember");
        classification.setMappings(Map.of("err.tpa.username.taken", usernameTakenMapping()));
        classification.setFallback(mapping("registration.complete.failed", false, null));
        return classification;
    }

    private DownstreamErrorClassificationMappingProperties usernameTakenMapping() {
        return mapping("registration.username.isregistered", true, 60);
    }

    private DownstreamErrorClassificationMappingProperties mapping(
            String responseCode,
            boolean idempotencyCacheable,
            Integer cacheTtlSeconds) {
        var mapping = new DownstreamErrorClassificationMappingProperties();
        mapping.setEventType("DOWNSTREAM_REJECTION");
        mapping.setLogLevel("ERROR");
        mapping.setResponseCode(responseCode);
        mapping.setTarget("PAGE");
        mapping.setIdempotencyCacheable(idempotencyCacheable);
        mapping.setCacheTtlSeconds(cacheTtlSeconds);
        return mapping;
    }
}
