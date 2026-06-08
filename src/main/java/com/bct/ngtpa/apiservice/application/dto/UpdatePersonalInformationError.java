package com.bct.ngtpa.apiservice.application.dto;

import java.util.Arrays;
import java.util.List;

public record UpdatePersonalInformationError(
        String type,
        List<String> fields,
        String code) {

    public UpdatePersonalInformationError {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public static UpdatePersonalInformationError fromPipeSeparatedFields(String type, String fields, String code) {
        List<String> parsedFields = fields == null || fields.isBlank()
                ? List.of()
                : Arrays.stream(fields.split("\\|"))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .toList();
        return new UpdatePersonalInformationError(type, parsedFields, code);
    }
}
