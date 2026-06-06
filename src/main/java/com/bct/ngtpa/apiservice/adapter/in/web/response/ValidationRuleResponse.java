package com.bct.ngtpa.apiservice.adapter.in.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValidationRuleResponse(
        String id,
        String type,
        Object value,
        Map<String, Object> when,
        Map<String, Object> then,
        String severity,
        String code,
        String message) {

    public ValidationRuleResponse {
        when = when == null || when.isEmpty() ? null : Map.copyOf(when);
        then = then == null || then.isEmpty() ? null : Map.copyOf(then);
    }

    @SuppressWarnings("unchecked")
    public static ValidationRuleResponse from(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        return new ValidationRuleResponse(
                stringValue(source.get("id")),
                stringValue(source.get("type")),
                source.get("value"),
                source.get("when") instanceof Map<?, ?> when ? (Map<String, Object>) when : null,
                source.get("then") instanceof Map<?, ?> then ? (Map<String, Object>) then : null,
                stringValue(source.get("severity")),
                stringValue(source.get("code")),
                stringValue(source.get("message")));
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
