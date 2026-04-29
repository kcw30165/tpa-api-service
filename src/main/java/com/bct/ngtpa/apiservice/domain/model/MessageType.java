package com.bct.ngtpa.apiservice.domain.model;

public enum MessageType {
    ACTION_REQUIRED("ACT_REQ", "ACTION REQUIRED"),
    DOCUMENT_AVAILABLE("DOC_AVAIL", "DOCUMENT AVAILABLE"),
    INVESTMENT_WORKSHOP("INV_WS", "INVESTMENT WORKSHOP"),
    MARKET_UPDATE("MKT_UPD", "MARKET UPDATE"),
    SYSTEM_MAINTENANCE("SYS_MAINT", "SYSTEM MAINTENANCE"),
    IMPORTANT_NOTICE("IMP_NOTE", "IMPORTANT NOTICE"),
    UNKNOWN("UNKNOWN", "UNKNOWN");

    private final String code;
    private final String title;

    MessageType(String code, String title) {
        this.code = code;
        this.title = title;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }

    public static MessageType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return UNKNOWN;
        }
        return switch (code.trim().toUpperCase().replace(" ", "_").replace("-", "_")) {
            case "ACT_REQ", "ACTION_REQUIRED" -> ACTION_REQUIRED;
            case "DOC_AVAIL", "DOCUMENT_AVAILABLE" -> DOCUMENT_AVAILABLE;
            case "INV_WS", "INVESTMENT_WORKSHOP" -> INVESTMENT_WORKSHOP;
            case "MKT_UPD", "MARKET_UPDATE" -> MARKET_UPDATE;
            case "SYS_MAINT", "SYSTEM_MAINTENANCE" -> SYSTEM_MAINTENANCE;
            case "IMP_NOTE", "IMPORTANT_NOTICE" -> IMPORTANT_NOTICE;
            default -> UNKNOWN;
        };
    }

    public static String titleFor(String code) {
        var messageType = fromCode(code);
        if (messageType == UNKNOWN && code != null && !code.isBlank()) {
            return code.trim();
        }
        return messageType.title();
    }
}
