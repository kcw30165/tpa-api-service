package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.DownstreamErrorClassificationMappingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.DownstreamErrorClassificationProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageSchemaProperties;
import com.bct.ngtpa.apiservice.application.dto.downstream.DownstreamErrorClassification;
import com.bct.ngtpa.apiservice.application.dto.downstream.DownstreamErrorClassificationRequest;
import com.bct.ngtpa.apiservice.application.port.out.DownstreamErrorClassificationPort;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class ConfigBackedDownstreamErrorClassificationAdapter implements DownstreamErrorClassificationPort {
    private static final String GENERIC_EVENT_TYPE = "DOWNSTREAM_REJECTION";
    private static final String GENERIC_LOG_LEVEL = "ERROR";
    private static final String GENERIC_RESPONSE_CODE = "err.workflow.downstream.businessRejected";
    private static final int GENERIC_HTTP_STATUS = 422;
    private static final String GENERIC_TARGET = "PAGE";

    private final BffPagesProperties properties;

    public ConfigBackedDownstreamErrorClassificationAdapter(BffPagesProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public DownstreamErrorClassification classify(DownstreamErrorClassificationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        PageSchemaProperties page = page(request.pageId());
        if (page == null || page.getDownstreamErrorClassification() == null) {
            return genericFallback();
        }

        DownstreamErrorClassificationProperties classification = page.getDownstreamErrorClassification();
        if (!matchesConfiguredValue(classification.getSource(), request.source())
                || !matchesConfiguredValue(classification.getEndpoint(), request.endpoint())) {
            return genericFallback();
        }

        DownstreamErrorClassificationMappingProperties explicit = mapping(
                classification.getExplicitMappings(),
                classification.getMappings(),
                request.downstreamErrorCode());
        if (explicit != null) {
            return toClassification(explicit, false);
        }
        if (classification.getFallback() != null) {
            return toClassification(classification.getFallback(), true);
        }
        return genericFallback();
    }

    private PageSchemaProperties page(String pageId) {
        return properties.getPages() == null ? null : properties.getPages().get(pageId);
    }

    private DownstreamErrorClassificationMappingProperties mapping(
            java.util.List<DownstreamErrorClassificationMappingProperties> explicitMappings,
            Map<String, DownstreamErrorClassificationMappingProperties> mappings,
            String downstreamErrorCode) {
        if (explicitMappings != null) {
            for (DownstreamErrorClassificationMappingProperties mapping : explicitMappings) {
                if (mapping != null
                        && mapping.getDownstreamErrorCode() != null
                        && mapping.getDownstreamErrorCode().trim().equals(downstreamErrorCode)) {
                    return mapping;
                }
            }
        }
        if (mappings == null || mappings.isEmpty()) {
            return null;
        }
        return mappings.get(downstreamErrorCode);
    }

    private boolean matchesConfiguredValue(String configured, String actual) {
        if (configured == null || configured.trim().isEmpty()) {
            return true;
        }
        return configured.trim().equals(actual == null ? null : actual.trim());
    }

    private DownstreamErrorClassification toClassification(
            DownstreamErrorClassificationMappingProperties mapping,
            boolean fallbackUsed) {
        return new DownstreamErrorClassification(
                textOrDefault(mapping.getEventType(), GENERIC_EVENT_TYPE),
                normalizeLogLevel(mapping.getLogLevel()),
                textOrDefault(mapping.getResponseCode(), GENERIC_RESPONSE_CODE),
                validHttpStatus(mapping.getHttpStatus()),
                textOrDefault(mapping.getTarget(), GENERIC_TARGET),
                Boolean.TRUE.equals(mapping.getIdempotencyCacheable()),
                mapping.getCacheTtlSeconds(),
                fallbackUsed);
    }

    private DownstreamErrorClassification genericFallback() {
        return new DownstreamErrorClassification(
                GENERIC_EVENT_TYPE,
                GENERIC_LOG_LEVEL,
                GENERIC_RESPONSE_CODE,
                GENERIC_HTTP_STATUS,
                GENERIC_TARGET,
                false,
                null,
                true);
    }

    private int validHttpStatus(Integer value) {
        if (value == null) {
            return GENERIC_HTTP_STATUS;
        }
        if (value != 400 && value != 403 && value != 409 && value != 422
                && value != 429 && value != 502 && value != 503) {
            throw new IllegalStateException("Unsupported downstream HTTP status: " + value);
        }
        return value;
    }

    private String normalizeLogLevel(String value) {
        String normalized = textOrDefault(value, GENERIC_LOG_LEVEL);
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String textOrDefault(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }
}
