package com.bct.ngtpa.apiservice.adapter.in.web.accountswitch;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code POST /api/v1/account/switch}.
 *
 * <p>The pattern mirrors {@code AuthenticatedAccountRef}'s confirmed format
 * ({@code ^acc_[A-Za-z0-9_-]{20,124}$}), so a malformed ref is rejected by {@code @Valid} before
 * ever reaching the session/account lookup.
 */
public record AccountSwitchRequest(
    @NotBlank
        @Pattern(regexp = "^acc_[A-Za-z0-9_-]{20,124}$", message = "Invalid account reference")
        String accountRef) {}
