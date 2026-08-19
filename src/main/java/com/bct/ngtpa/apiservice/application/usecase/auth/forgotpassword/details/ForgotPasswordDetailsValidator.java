package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ForgotPasswordDetailsValidator {
  private static final DateTimeFormatter DOB =
      DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
  private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
  private static final Pattern HK_MOBILE = Pattern.compile("^(?:\\+?852)?[456789]\\d{7}$");

  public ForgotPasswordDetailsValidation validate(String dob, String email, String mobile) {
    var targets = new ArrayList<String>();
    try {
      LocalDate.parse(dob == null ? "" : dob.trim(), DOB);
    } catch (RuntimeException ignored) {
      targets.add("dateOfBirth");
    }
    boolean hasEmail = email != null && !email.isBlank();
    boolean hasMobile = mobile != null && !mobile.isBlank();
    if (hasEmail == hasMobile) {
      targets.add("email");
      targets.add("mobile");
    } else if (hasEmail && !EMAIL.matcher(email.trim()).matches()) {
      targets.add("email");
    } else if (hasMobile && !HK_MOBILE.matcher(mobile.replaceAll("[\\s-]", "")).matches()) {
      targets.add("mobile");
    }
    return new ForgotPasswordDetailsValidation(targets.isEmpty(), List.copyOf(targets));
  }
}
