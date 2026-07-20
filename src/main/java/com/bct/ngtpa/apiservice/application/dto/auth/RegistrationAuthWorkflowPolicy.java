package com.bct.ngtpa.apiservice.application.dto.auth;

public record RegistrationAuthWorkflowPolicy(
        PreStartChallengePolicy preStartChallenge,
        CaptchaPolicy captcha,
        OtpPolicy otp,
        UsernamePolicy username,
        PasswordPolicy password) {
}
