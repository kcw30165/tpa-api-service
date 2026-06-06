package com.bct.ngtpa.apiservice.adapter.in.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldResponse(
        String name,
        String label,
        String dataType,
        String controlType,

        @JsonInclude(JsonInclude.Include.ALWAYS)
        Object value,

        @JsonInclude(JsonInclude.Include.ALWAYS)
        Object originalValue,

        Boolean readonly,
        Boolean required,
        Integer minLength,
        Integer maxLength,
        String pattern,
        String placeholder,
        String optionSource,
        Map<String, Object> copyWhenChecked,
        Integer displayOrder,
        List<ValidationRuleResponse> validations) {

    public FieldResponse {
        copyWhenChecked = copyWhenChecked == null || copyWhenChecked.isEmpty() ? null : Map.copyOf(copyWhenChecked);
        validations = validations == null || validations.isEmpty() ? null : List.copyOf(validations);
    }
}