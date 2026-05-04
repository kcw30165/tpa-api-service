package com.bct.ngtpa.apiservice.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MessageStatusTest {

    @ParameterizedTest
    @MethodSource("fromCodeCases")
    void mapsCodesToExpectedStatus(String code, MessageStatus expectedStatus) {
        assertEquals(expectedStatus, MessageStatus.fromCode(code));
    }

    @ParameterizedTest
    @MethodSource("readCases")
    void reportsReadFlag(MessageStatus status, boolean expectedRead) {
        assertEquals(expectedRead, status.isRead());
    }

    private static Stream<Arguments> fromCodeCases() {
        return Stream.of(
                Arguments.of(null, MessageStatus.UNKNOWN),
                Arguments.of("", MessageStatus.UNKNOWN),
                Arguments.of("   ", MessageStatus.UNKNOWN),
                Arguments.of("READ", MessageStatus.READ),
                Arguments.of(" read ", MessageStatus.READ),
                Arguments.of("R", MessageStatus.READ),
                Arguments.of("UNREAD", MessageStatus.UNREAD),
                Arguments.of(" u ", MessageStatus.UNREAD),
                Arguments.of("other", MessageStatus.UNKNOWN));
    }

    private static Stream<Arguments> readCases() {
        return Stream.of(
                Arguments.of(MessageStatus.READ, true),
                Arguments.of(MessageStatus.UNREAD, false),
                Arguments.of(MessageStatus.UNKNOWN, false));
    }
}