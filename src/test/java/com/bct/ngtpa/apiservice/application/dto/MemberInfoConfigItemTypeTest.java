package com.bct.ngtpa.apiservice.application.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MemberInfoConfigItemTypeTest {

    @ParameterizedTest
    @CsvSource({
            "UI,UI",
            "ui,UI",
            " DATA ,DATA",
            "RULE,RULE",
            "unexpected,UNKNOWN",
            "'',UNKNOWN"
    })
    void mapsApimItemTypeCodeSafely(String code, MemberInfoConfigItemType expected) {
        assertEquals(expected, MemberInfoConfigItemType.fromCode(code));
    }
}
