package com.bct.ngtpa.apiservice.application.dto;

import java.util.Locale;

public enum MemberInfoConfigItemType {
    UI,
    DATA,
    RULE,
    UNKNOWN;

    public static MemberInfoConfigItemType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return UNKNOWN;
        }
        try {
            return MemberInfoConfigItemType.valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }
}
