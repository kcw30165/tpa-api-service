package com.bct.ngtpa.apiservice.application.usecase.auth.login.preauth;

import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationCommand;
import java.util.Objects;
import java.util.regex.Pattern;

public final class LoginCredentialPolicyService {
    private final boolean usernameTrim;
    private final LoginUsernameNormalization normalization;
    private final int usernameMinLength;
    private final int usernameMaxLength;
    private final Pattern usernamePattern;
    private final boolean passwordTrim;
    private final boolean passwordCaseSensitive;
    private final int passwordMinLength;
    private final int passwordMaxLength;

    public LoginCredentialPolicyService(
            boolean usernameTrim,
            LoginUsernameNormalization normalization,
            int usernameMinLength,
            int usernameMaxLength,
            String usernameAllowedPattern,
            boolean passwordTrim,
            boolean passwordCaseSensitive,
            int passwordMinLength,
            int passwordMaxLength) {
        this.usernameTrim = usernameTrim;
        this.normalization = Objects.requireNonNull(normalization);
        this.usernameMinLength = usernameMinLength;
        this.usernameMaxLength = usernameMaxLength;
        this.usernamePattern = Pattern.compile(Objects.requireNonNull(usernameAllowedPattern));
        this.passwordTrim = passwordTrim;
        this.passwordCaseSensitive = passwordCaseSensitive;
        this.passwordMinLength = passwordMinLength;
        this.passwordMaxLength = passwordMaxLength;
        if (!passwordCaseSensitive || passwordTrim) {
            throw new IllegalArgumentException("Login password policy must preserve exact case-sensitive value");
        }
    }

    public CredentialAuthenticationCommand validateAndNormalize(CredentialAuthenticationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String username = command.username();
        if (usernameTrim && username != null)
            username = username.trim();
        if (normalization == LoginUsernameNormalization.ASCII_LOWERCASE && username != null) {
            StringBuilder value = new StringBuilder(username.length());
            for (char ch : username.toCharArray()) {
                value.append(ch >= 'A' && ch <= 'Z' ? (char) (ch + ('a' - 'A')) : ch);
            }
            username = value.toString();
        }
        validate(username, usernameMinLength, usernameMaxLength, "username");
        if (!usernamePattern.matcher(username).matches()) {
            throw new InvalidLoginCredentialsException("Login username does not match configured policy");
        }
        String password = command.password();
        validate(password, passwordMinLength, passwordMaxLength, "password");
        return new CredentialAuthenticationCommand(
                username, password, command.acceptLanguage(), command.correlationId());
    }

    private void validate(String value, int min, int max, String field) {
        if (value == null || value.length() < min || value.length() > max) {
            throw new InvalidLoginCredentialsException("Login " + field + " is invalid");
        }
    }
}
