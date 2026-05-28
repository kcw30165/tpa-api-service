package com.bct.ngtpa.apiservice.application.dto;

public enum TermStatus {
    BLANK,
    O,
    P,
    S,
    T,
    UNKNOWN;

    public static TermStatus fromCode(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return BLANK;
        }
        return switch (rawValue.trim().toUpperCase()) {
            case "O" -> O;
            case "P" -> P;
            case "S" -> S;
            case "T" -> T;
            default -> UNKNOWN;
        };
    }
}