package com.bct.ngtpa.apiservice.shared.config;

/**
 * Shared, pure classification of a single APIM config value into the BFF field-access facets
 * (hidden / readonly / required). The downstream enum is exactly HIDDEN, READONLY, EDITABLE_COM and
 * EDITABLE_OPTION (case-insensitive, surrounding whitespace ignored). A non-blank value outside
 * that enum is "unrecognised" (D-32) and a null or blank value is "blank" (D-32a). Both are treated
 * as HIDDEN and callers log them at ERROR; neither is readonly nor required.
 *
 * <p>{@code requiredOverride} (D-25a) can only lower the requirement of an EDITABLE_COM field.
 */
public final class FieldAccessResolver {

  private static final String HIDDEN = "HIDDEN";
  private static final String READONLY = "READONLY";
  private static final String EDITABLE_COM = "EDITABLE_COM";
  private static final String EDITABLE_OPTION = "EDITABLE_OPTION";

  private FieldAccessResolver() {}

  public static boolean isHidden(String configValue) {
    return HIDDEN.equalsIgnoreCase(normalise(configValue))
        || isBlank(configValue)
        || isUnrecognised(configValue);
  }

  public static boolean isReadonly(String configValue) {
    return READONLY.equalsIgnoreCase(normalise(configValue));
  }

  public static boolean isRequired(String configValue) {
    return EDITABLE_COM.equalsIgnoreCase(normalise(configValue));
  }

  public static boolean isRequired(String configValue, Boolean requiredOverride) {
    return isRequired(configValue) && !Boolean.FALSE.equals(requiredOverride);
  }

  public static boolean isBlank(String configValue) {
    String normalised = normalise(configValue);
    return normalised == null || normalised.isEmpty();
  }

  public static boolean isUnrecognised(String configValue) {
    if (isBlank(configValue)) {
      return false;
    }
    String normalised = normalise(configValue);
    return !(HIDDEN.equalsIgnoreCase(normalised)
        || READONLY.equalsIgnoreCase(normalised)
        || EDITABLE_COM.equalsIgnoreCase(normalised)
        || EDITABLE_OPTION.equalsIgnoreCase(normalised));
  }

  private static String normalise(String configValue) {
    return configValue == null ? null : configValue.trim();
  }
}
