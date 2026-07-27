package com.bct.ngtpa.apiservice.application.dto.auth.login.account;

import java.util.List;
import java.util.Objects;

public record LoginAccountValidationResult(LoginAccountValidationStatus status, List<LoginValidatedAccount> accounts,
        List<LoginAccountRecordRejection> rejections, int returnedCount) {
    public LoginAccountValidationResult {
        Objects.requireNonNull(status);
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
        rejections = rejections == null ? List.of() : List.copyOf(rejections);
        if (returnedCount < 0)
            throw new IllegalArgumentException("returnedCount must not be negative");
        if (status == LoginAccountValidationStatus.VALID && accounts.isEmpty())
            throw new IllegalArgumentException("VALID requires accounts");
        if (status == LoginAccountValidationStatus.ZERO_VALID_RECORDS && !accounts.isEmpty())
            throw new IllegalArgumentException("ZERO_VALID_RECORDS cannot carry accounts");
    }

    public int rejectedCount() {
        return rejections.size();
    }

    @Override
    public String toString() {
        return "LoginAccountValidationResult[status=" + status + ", validCount=" + accounts.size() + ", rejectedCount="
                + rejections.size() + ", returnedCount=" + returnedCount + "]";
    }
}
