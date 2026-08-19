package com.bct.ngtpa.apiservice.application.dto;

public record FetchContributionSummaryCommand(
    String policyNo,
    String certNo,
    String userId,
    String coverFrom,
    String coverTo,
    String accountEnv,
    ActorUserRole actorUserRole) {}
