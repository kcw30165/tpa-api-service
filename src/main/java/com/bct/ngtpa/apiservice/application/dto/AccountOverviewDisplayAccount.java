package com.bct.ngtpa.apiservice.application.dto;

public record AccountOverviewDisplayAccount(
    String displayAccountId,
    String displayAccountLabel,
    Integer displaySequence,
    String accountBalance,
    String vestedBalance) {}
