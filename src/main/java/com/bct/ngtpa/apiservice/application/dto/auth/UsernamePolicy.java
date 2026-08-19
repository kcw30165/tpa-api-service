package com.bct.ngtpa.apiservice.application.dto.auth;

public record UsernamePolicy(
    int minLength,
    int maxLength,
    boolean noSpaces,
    boolean caseInsensitive,
    boolean requiredDigit,
    boolean requiredSpecial,
    String allowedSpecialChars) {}
