package com.bct.ngtpa.apiservice.domain.model;

public enum MessageStatus {
    READ,
    UNREAD,
    UNKNOWN;

    public boolean isRead() {
        return this == READ;
    }

    public static MessageStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return UNKNOWN;
        }
        return switch (code.trim().toUpperCase()) {
            case "READ", "R"     -> READ;
            case "UNREAD", "U"   -> UNREAD;
            default              -> UNKNOWN;
        };
    }
}
