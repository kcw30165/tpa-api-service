package com.bct.ngtpa.apiservice.application.dto.session.authflow;

import java.util.Map;

public record AuthFlowSessionData(
        String contactType,
        String maskedDestination,
        Map<String, String> attributes) {
    public AuthFlowSessionData {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static AuthFlowSessionData empty() {
        return new AuthFlowSessionData(null, null, Map.of());
    }

    @Override
    public String toString() {
        return "AuthFlowSessionData[contactType=" + contactType
                + ", maskedDestination=" + maskedDestination
                + ", attributesSize=" + attributes.size() + "]";
    }
}
