package com.bct.ngtpa.apiservice.application.dto.auth;

public record PasswordPolicy(
                int minLength,
                int maxLength,
                boolean noSpaces,
                boolean notSameAsUsernameIgnoreCase,
                int requiredCategories,
                String specialChars) {
}