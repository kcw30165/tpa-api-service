package com.bct.ngtpa.apiservice.application.dto;

public record GetPersonalInformationCommand(
        String language, String accountEnv, String policyNo, String certNo, String actorUserId) {
}
