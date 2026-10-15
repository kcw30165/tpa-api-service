package com.bct.ngtpa.apiservice.adapter.in.web.account;

/** Per-account display fields, shared by both the single-get and list-all responses. */
public record AccountDetailsResponse(
    String accountRef,
    String policyNo,
    String certNo,
    String accountEnv,
    String trustCode,
    String schemeType) {}
