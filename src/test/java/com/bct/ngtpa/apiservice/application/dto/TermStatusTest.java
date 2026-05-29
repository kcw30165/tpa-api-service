package com.bct.ngtpa.apiservice.application.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TermStatusTest {

    @Test
    void fromCodeReturnsBlankForNullOrBlankInput() {
        assertEquals(TermStatus.BLANK, TermStatus.fromCode(null));
        assertEquals(TermStatus.BLANK, TermStatus.fromCode(""));
        assertEquals(TermStatus.BLANK, TermStatus.fromCode("   "));
    }

    @Test
    void fromCodeReturnsKnownStatusesForSupportedCodes() {
        assertEquals(TermStatus.O, TermStatus.fromCode("O"));
        assertEquals(TermStatus.P, TermStatus.fromCode("P"));
        assertEquals(TermStatus.S, TermStatus.fromCode("S"));
        assertEquals(TermStatus.T, TermStatus.fromCode("T"));
    }

    @Test
    void fromCodeNormalizesCaseForSupportedCodes() {
        assertEquals(TermStatus.O, TermStatus.fromCode("o"));
        assertEquals(TermStatus.P, TermStatus.fromCode("p"));
        assertEquals(TermStatus.S, TermStatus.fromCode("s"));
        assertEquals(TermStatus.T, TermStatus.fromCode("t"));
    }

    @Test
    void fromCodeReturnsUnknownForUnsupportedNonBlankValues() {
        assertEquals(TermStatus.UNKNOWN, TermStatus.fromCode("anything-else"));
    }
}