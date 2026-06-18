package com.bct.ngtpa.apiservice.application.dto;

public record RefreshReferenceDateCommand(String accountEnv) {

    public RefreshReferenceDateCommand {
        if (accountEnv == null || accountEnv.isBlank()) {
            throw new IllegalArgumentException("accountEnv must not be blank");
        }
    }
}