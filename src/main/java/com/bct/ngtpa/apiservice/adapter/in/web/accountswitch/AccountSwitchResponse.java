package com.bct.ngtpa.apiservice.adapter.in.web.accountswitch;

/** Response payload for a successful {@code POST /api/v1/account/switch}. */
public record AccountSwitchResponse(
    String accountRef,
    String policyNo,
    String certNo,
    String accountEnv,
    String trust,
    String schemeType) {}
