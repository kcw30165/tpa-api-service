package com.bct.ngtpa.apiservice.application.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public record UpdatePersonalInformationCommand(
        String accountRef,
        Boolean applyToAllAccounts,
        Map<String, Object> updateFields) {

    public UpdatePersonalInformationCommand {
        accountRef = normalize(accountRef);
        updateFields = updateFields == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(updateFields));
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
