package com.bct.ngtpa.apiservice.adapter.in.web.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshReferenceDateRequest(
        @NotBlank(message = "accountEnv must not be blank")
        String accountEnv) {
}