package com.bct.ngtpa.apiservice.application.dto.auth;

public record RegistrationRateLimitPolicy(
        RateLimitRulePolicy registrationStartByIp,
        RateLimitRulePolicy otpSendByDestinationHash,
        RateLimitRulePolicy otpVerifyByRegistrationSession) {
}
