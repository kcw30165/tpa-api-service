package com.bct.ngtpa.apiservice.adapter.out.apim.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class GetMessageBoardApimRequestTest {

    private static final Pattern REF_DATE_PATTERN = Pattern.compile("\\d{2}/\\d{2}/\\d{4}");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getRefDateGeneratesDefaultWhenValueIsNull() {
        GetMessageBoardApimRequest request = GetMessageBoardApimRequest.builder()
                .refDate(null)
                .build();

        assertDefaultRefDate(request.getRefDate());
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "   "})
    void getRefDateGeneratesDefaultWhenValueIsBlank(String input) {
        GetMessageBoardApimRequest request = GetMessageBoardApimRequest.builder()
                .refDate(input)
                .build();

        assertDefaultRefDate(request.getRefDate());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void setRefDateGeneratesDefaultWhenInputHasNoText(String input) {
        GetMessageBoardApimRequest request = new GetMessageBoardApimRequest();

        request.setRefDate(input);

        assertDefaultRefDate(request.getRefDate());
    }

    @Test
    void setRefDatePreservesExplicitValue() {
        GetMessageBoardApimRequest request = new GetMessageBoardApimRequest();

        request.setRefDate("17/04/2026");

        assertEquals("17/04/2026", request.getRefDate());
    }

    @Test
    void deserializingNullRefDateKeepsBuilderDefault() throws Exception {
        GetMessageBoardApimRequest request = objectMapper.readValue(
                "{" +
                        "\"policy-no\":\"P1\"," +
                        "\"ref-date\":null" +
                        "}",
                GetMessageBoardApimRequest.class);

        assertEquals("P1", request.getPolicyNo());
        assertDefaultRefDate(request.getRefDate());
    }

    private static void assertDefaultRefDate(String value) {
        assertNotNull(value);
        assertTrue(REF_DATE_PATTERN.matcher(value).matches(), () -> "Unexpected ref date: " + value);
    }
}