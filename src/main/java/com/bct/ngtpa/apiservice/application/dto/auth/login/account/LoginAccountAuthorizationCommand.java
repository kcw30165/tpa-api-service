package com.bct.ngtpa.apiservice.application.dto.auth.login.account;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.util.Objects;

/** Exact transport-neutral input required by TRPGetAccountList. */
public record LoginAccountAuthorizationCommand(String custId, String custType, String searchKey, String searchValue,
        String correlationId, ActorUserRole actorUserRole) {
    public LoginAccountAuthorizationCommand {
        custId = required(custId, "custId");
        custType = required(custType, "custType");
        searchKey = required(searchKey, "searchKey");
        searchValue = required(searchValue, "searchValue");
        correlationId = optional(correlationId);
        Objects.requireNonNull(actorUserRole, "actorUserRole must not be null");
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    @Override public String toString() {
        return "LoginAccountAuthorizationCommand[custId=<redacted>, custType=" + custType + ", searchKey=" + searchKey
                + ", searchValue=<redacted>, correlationId=" + correlationId + ", actorUserRole=" + actorUserRole + "]";
    }
}
