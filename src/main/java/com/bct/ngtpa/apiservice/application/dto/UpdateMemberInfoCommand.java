package com.bct.ngtpa.apiservice.application.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public record UpdateMemberInfoCommand(
        String accountEnv,
        String policyNo,
        String certNo,
        String userId,
        String userRole,
        Boolean applyToAllAccounts,
        Map<String, Object> updateFields) {

    public UpdateMemberInfoCommand {
        updateFields = updateFields == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(updateFields));
    }
}
