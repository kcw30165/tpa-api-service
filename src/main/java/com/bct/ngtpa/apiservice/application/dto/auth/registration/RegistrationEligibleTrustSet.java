package com.bct.ngtpa.apiservice.application.dto.auth.registration;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/** Server-owned eligible trust set established by the verified registration lookup. */
public final class RegistrationEligibleTrustSet {
  public static final String ATTRIBUTE_NAME = "registrationEligibleTrusts";
  private static final String ENTRY_SEPARATOR = ",";
  private static final String FIELD_SEPARATOR = ".";

  private RegistrationEligibleTrustSet() {}

  public static String encode(List<RegistrationLookupAccount> accounts) {
    Objects.requireNonNull(accounts, "accounts must not be null");
    if (accounts.isEmpty()) throw new IllegalArgumentException("accounts must not be empty");
    return accounts.stream()
        .map(
            a ->
                encodeField(required(a.accountEnv(), "accountEnv"))
                    + FIELD_SEPARATOR
                    + encodeField(required(a.trustCode(), "trustCode")))
        .distinct()
        .sorted()
        .reduce((left, right) -> left + ENTRY_SEPARATOR + right)
        .orElseThrow();
  }

  public static boolean contains(String encoded, RegistrationSelectedTrust selected) {
    if (encoded == null || encoded.isBlank() || selected == null) return false;
    String expected =
        encodeField(required(selected.accountEnv(), "accountEnv"))
            + FIELD_SEPARATOR
            + encodeField(required(selected.trustCode(), "trustCode"));
    return List.of(encoded.split(ENTRY_SEPARATOR, -1)).contains(expected);
  }

  private static String encodeField(String value) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String required(String value, String name) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(name + " must not be blank");
    return value.trim();
  }
}
