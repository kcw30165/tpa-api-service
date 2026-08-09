package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartCommand;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Registration-compatible validation boundary for the Username Recovery start command.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-api-contract-design.md}
 * section 4 (identity type/value, strict {@code dd/MM/yyyy} DOB, exactly-one email/mobile,
 * email/mobile reuse Registration validation; UR-START-002..006) and
 * {@code docs/design/6-username-recovery/username-recovery-solution-design.md} section 4
 * (identity-value validation reuses Registration behavior).
 *
 * <p>Typed {@link Error} entries carry a frontend field target and a {@link Kind}; the derived
 * normalized verified destination is produced only for a valid command. Sensitive values are
 * never retained beyond the returned immutable value objects.
 */
public final class UsernameRecoveryStartValidator {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern HK_MOBILE = Pattern.compile("^[4-9][0-9]{7}$");
    private static final Pattern GPN = Pattern.compile("^[0-9]{8}$");
    private static final Pattern GPFS = Pattern.compile("^[0-9]{10}$");
    private static final Pattern STAFFNO = Pattern.compile("^[A-Za-z0-9]+$");
    private static final Pattern NO_SPACE_MAX_40 = Pattern.compile("^\\S{1,40}$");
    private static final DateTimeFormatter DATE_OF_BIRTH_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    public enum Kind {
        IDENTITY_VALUE_INVALID,
        DATE_OF_BIRTH_INVALID,
        CONTACT_REQUIRED,
        CONTACT_EXACTLY_ONE_REQUIRED,
        EMAIL_INVALID,
        MOBILE_INVALID,
        IDEMPOTENCY_KEY_INVALID
    }

    /** Typed validation error: frontend field target plus canonical kind. */
    public record Error(String target, Kind kind) {
        public Error {
            Objects.requireNonNull(target, "target must not be null");
            Objects.requireNonNull(kind, "kind must not be null");
        }

        @Override
        public String toString() {
            return "Error[target=" + target + ", kind=" + kind + "]";
        }
    }

    /** Validation outcome: valid flag, typed errors, normalized contacts, and verified destination. */
    public record Validation(
            boolean valid,
            List<Error> errors,
            String email,
            String mobile,
            UsernameRecoveryVerifiedDestination destination) {

        public Validation {
            errors = errors == null ? List.of() : List.copyOf(errors);
            if (valid && destination == null) {
                throw new IllegalArgumentException("valid validation requires a destination");
            }
            if (!valid && destination != null) {
                throw new IllegalArgumentException("invalid validation must not carry a destination");
            }
        }
    }

    public Validation validate(UsernameRecoveryStartCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        List<Error> errors = new ArrayList<>();

        String identityValue = command.identityValue().trim();
        if (!isValidIdentityValue(command.identityType(), identityValue)) {
            errors.add(new Error("identityValue", Kind.IDENTITY_VALUE_INVALID));
        }

        if (!isValidDateOfBirth(command.dateOfBirth())) {
            errors.add(new Error("dateOfBirth", Kind.DATE_OF_BIRTH_INVALID));
        }

        String email = normalizeEmail(command.email());
        String mobile = normalizeMobile(command.mobile());
        boolean hasEmail = !email.isBlank();
        boolean hasMobile = !mobile.isBlank();
        if (!hasEmail && !hasMobile) {
            errors.add(new Error("email", Kind.CONTACT_REQUIRED));
            errors.add(new Error("mobile", Kind.CONTACT_REQUIRED));
        } else if (hasEmail && hasMobile) {
            errors.add(new Error("email", Kind.CONTACT_EXACTLY_ONE_REQUIRED));
            errors.add(new Error("mobile", Kind.CONTACT_EXACTLY_ONE_REQUIRED));
        } else if (hasEmail && !isValidEmail(email)) {
            errors.add(new Error("email", Kind.EMAIL_INVALID));
        } else if (hasMobile && !HK_MOBILE.matcher(mobile).matches()) {
            errors.add(new Error("mobile", Kind.MOBILE_INVALID));
        }

        if (!errors.isEmpty()) {
            return new Validation(false, errors, email, mobile, null);
        }

        UsernameRecoveryVerifiedDestination destination = hasEmail
                ? new UsernameRecoveryVerifiedDestination(email,
                        UsernameRecoveryVerifiedDestination.Channel.EMAIL)
                : new UsernameRecoveryVerifiedDestination(mobile,
                        UsernameRecoveryVerifiedDestination.Channel.MOBILE);
        return new Validation(true, List.of(), email, mobile, destination);
    }

    private static boolean isValidIdentityValue(MemberIdentityType type, String value) {
        if (type == null || value.isBlank()) {
            return false;
        }
        return switch (type) {
            case GPN -> GPN.matcher(value).matches();
            case GPFS -> GPFS.matcher(value).matches();
            case STAFFNO -> STAFFNO.matcher(value).matches();
            case ID -> NO_SPACE_MAX_40.matcher(value).matches();
        };
    }

    private static boolean isValidDateOfBirth(String value) {
        if (value == null || value.trim().isBlank()) {
            return false;
        }
        try {
            LocalDate.parse(value.trim(), DATE_OF_BIRTH_FORMATTER);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private static boolean isValidEmail(String email) {
        return email.length() <= 120
                && EMAIL.matcher(email).matches()
                && email.indexOf('@') == email.lastIndexOf('@')
                && !email.startsWith("@")
                && !email.endsWith("@");
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeMobile(String mobile) {
        return mobile == null ? "" : mobile.trim();
    }
}

