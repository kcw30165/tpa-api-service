package com.bct.ngtpa.apiservice.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MessageTypeTest {

    @ParameterizedTest
    @MethodSource("fromCodeCases")
    void mapsCodesToExpectedType(String code, MessageType expectedType) {
        assertEquals(expectedType, MessageType.fromCode(code));
    }

    @ParameterizedTest
    @MethodSource("titleCases")
    void resolvesTitlesFromCodes(String code, String expectedTitle) {
        assertEquals(expectedTitle, MessageType.titleFor(code));
    }

    private static Stream<Arguments> fromCodeCases() {
        return Stream.of(
                Arguments.of(null, MessageType.UNKNOWN),
                Arguments.of("", MessageType.UNKNOWN),
                Arguments.of("  ", MessageType.UNKNOWN),
                Arguments.of("ACT_REQ", MessageType.ACTION_REQUIRED),
                Arguments.of("action required", MessageType.ACTION_REQUIRED),
                Arguments.of("DOC-AVAIL", MessageType.DOCUMENT_AVAILABLE),
                Arguments.of("investment_workshop", MessageType.INVESTMENT_WORKSHOP),
                Arguments.of("mkt_upd", MessageType.MARKET_UPDATE),
                Arguments.of("system-maintenance", MessageType.SYSTEM_MAINTENANCE),
                Arguments.of("important notice", MessageType.IMPORTANT_NOTICE),
                Arguments.of("unexpected", MessageType.UNKNOWN));
    }

    private static Stream<Arguments> titleCases() {
        return Stream.of(
                Arguments.of("ACT_REQ", "ACTION REQUIRED"),
                Arguments.of("important notice", "IMPORTANT NOTICE"),
                Arguments.of(" custom-value ", "custom-value"),
                Arguments.of("", "UNKNOWN"),
                Arguments.of(null, "UNKNOWN"));
    }
}