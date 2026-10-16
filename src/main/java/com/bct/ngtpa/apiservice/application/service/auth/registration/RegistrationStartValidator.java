package com.bct.ngtpa.apiservice.application.service.auth.registration;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationStartContact;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationStartInput;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationStartValidationError;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationStartValidationResult;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorKey;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class RegistrationStartValidator {
  private static final String TARGET_EMAIL = "email";

  private static final String TARGET_MOBILE = "mobile";

  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s.]+\\.[^@\\s]+$");
  private static final Pattern HK_MOBILE = Pattern.compile("^[4-9][0-9]{7}$");
  private static final Pattern SAFE_CAPTCHA = Pattern.compile("^[A-Za-z0-9_-]{6,128}$");
  private final RegistrationContactDeriver contactDeriver;

  public RegistrationStartValidator(MaskingService maskingService) {
    this.contactDeriver =
        new RegistrationContactDeriver(
            Objects.requireNonNull(maskingService, "maskingService must not be null"));
  }

  public RegistrationStartValidationResult validate(RegistrationStartInput input) {
    List<RegistrationStartValidationError> errors = new ArrayList<>();
    MemberIdentityType identityType =
        parseIdentityType(input == null ? null : input.identityType(), errors);
    String identityValue = normalize(input == null ? null : input.identityValue());
    String dateOfBirth = normalize(input == null ? null : input.dateOfBirth());
    String email = RegistrationContactDeriver.normalizeEmail(input == null ? null : input.email());
    String mobile =
        RegistrationContactDeriver.normalizeMobile(input == null ? null : input.mobile());
    String captchaChallengeId = normalize(input == null ? null : input.captchaChallengeId());

    if (identityType != null
        && !RegistrationIdentityValueValidator.isValid(identityType, identityValue)) {
      errors.add(error(RegistrationErrorKey.REGISTRATION_IDENTITYVALUE_INVALID, "identityValue"));
    } else if (identityType == null && identityValue.isBlank()) {
      errors.add(error(RegistrationErrorKey.REGISTRATION_IDENTITYVALUE_INVALID, "identityValue"));
    }

    if (!RegistrationDateOfBirthValidator.isValid(dateOfBirth)) {
      errors.add(error(RegistrationErrorKey.REGISTRATION_DATEOFBIRTH_INVALID, "dateOfBirth"));
    }

    boolean hasEmail = !email.isBlank();
    boolean hasMobile = !mobile.isBlank();
    if (!hasEmail && !hasMobile) {
      errors.add(error(RegistrationErrorKey.REGISTRATION_CONTACT_REQUIRED, TARGET_EMAIL));
      errors.add(error(RegistrationErrorKey.REGISTRATION_CONTACT_REQUIRED, TARGET_MOBILE));
    } else if (hasEmail && hasMobile) {
      errors.add(error(RegistrationErrorKey.REGISTRATION_CONTACT_EXACTLYONEREQUIRED, TARGET_EMAIL));
      errors.add(
          error(RegistrationErrorKey.REGISTRATION_CONTACT_EXACTLYONEREQUIRED, TARGET_MOBILE));
    } else if (hasEmail && !isValidEmail(email)) {
      errors.add(error(RegistrationErrorKey.REGISTRATION_EMAIL_INVALID, TARGET_EMAIL));
    } else if (hasMobile && !HK_MOBILE.matcher(mobile).matches()) {
      errors.add(error(RegistrationErrorKey.REGISTRATION_MOBILE_INVALID, TARGET_MOBILE));
    }

    if (!captchaChallengeId.isBlank() && !SAFE_CAPTCHA.matcher(captchaChallengeId).matches()) {
      errors.add(error(RegistrationErrorKey.REGISTRATION_CAPTCHA_INVALID, "captchaChallengeId"));
    }

    if (!errors.isEmpty()) {
      return RegistrationStartValidationResult.invalid(errors);
    }

    RegistrationStartContact contact = contactDeriver.derive(email, mobile);
    return RegistrationStartValidationResult.valid(
        new RegistrationStartCommand(
            identityType,
            identityValue,
            dateOfBirth,
            contact.contactType(),
            contact.contactValue(),
            contact.maskedDestination(),
            captchaChallengeId));
  }

  private static MemberIdentityType parseIdentityType(
      String raw, List<RegistrationStartValidationError> errors) {
    String value = normalize(raw);
    if (value.isBlank()) {
      errors.add(error(RegistrationErrorKey.REGISTRATION_IDENTITYTYPE_INVALID, "identityType"));
      return null;
    }
    try {
      return "STAFF_NO".equals(value)
          ? MemberIdentityType.STAFFNO
          : MemberIdentityType.valueOf(value);
    } catch (IllegalArgumentException exception) {
      errors.add(error(RegistrationErrorKey.REGISTRATION_IDENTITYTYPE_INVALID, "identityType"));
      return null;
    }
  }

  private static boolean isValidEmail(String email) {
    return email.length() <= 120
        && EMAIL.matcher(email).matches()
        && email.indexOf('@') == email.lastIndexOf('@')
        && !email.startsWith("@")
        && !email.endsWith("@");
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  private static RegistrationStartValidationError error(WorkflowErrorKey key, String target) {
    return new RegistrationStartValidationError(key, target, "LOCAL");
  }
}
