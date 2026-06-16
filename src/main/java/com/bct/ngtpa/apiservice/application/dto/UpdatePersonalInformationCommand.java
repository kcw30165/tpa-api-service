package com.bct.ngtpa.apiservice.application.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public record UpdatePersonalInformationCommand(
        Boolean applyToAllAccounts,
        Map<String, Object> updateFields) {

    public UpdatePersonalInformationCommand {
        updateFields = updateFields == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(updateFields));
    }

    public UpdatePersonalInformationCommand(
            String ignoredLegacySelectedAccount,
            Boolean applyToAllAccounts,
            Map<String, Object> updateFields) {
        this(applyToAllAccounts, updateFields);
    }
}
