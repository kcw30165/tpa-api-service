package com.bct.ngtpa.apiservice.domain.model;

public enum MessageType {
    ACTION_REQUIRED,
    DOCUMENT_AVAILABLE,
    INVESTMENT_WORKSHOP,
    MARKET_UPDATE,
    SYSTEM_MAINTENANCE,
    UNKNOWN;

    public static MessageType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return UNKNOWN;
        }
        return switch (code.trim().toUpperCase().replace(" ", "_").replace("-", "_")) {
            case "ACTION_REQUIRED"     -> ACTION_REQUIRED;
            case "DOCUMENT_AVAILABLE"  -> DOCUMENT_AVAILABLE;
            case "INVESTMENT_WORKSHOP" -> INVESTMENT_WORKSHOP;
            case "MARKET_UPDATE"       -> MARKET_UPDATE;
            case "SYSTEM_MAINTENANCE"  -> SYSTEM_MAINTENANCE;
            default                    -> UNKNOWN;
        };
    }
}
