package com.bct.ngtpa.apiservice.shared.config;

/**
 * Shared, pure classification of a single APIM config value into the three BFF field-access facets
 * (hidden / readonly / required). Extracted from PersonalInformationWebMapper's two historically
 * duplicated config-resolution paths (typed MemberInfoConfigItem and legacy Map&lt;String,
 * String&gt;) so both GET and, in a later sub-task, PUT share one definition.
 */
public final class FieldAccessResolver {

  private FieldAccessResolver() {}

  public static boolean isHidden(String configValue) {
    return "HIDDEN".equalsIgnoreCase(configValue);
  }

  public static boolean isReadonly(String configValue) {
    return "READONLY".equalsIgnoreCase(configValue);
  }

  public static boolean isRequired(String configValue) {
    return "EDITABLE_COM".equalsIgnoreCase(configValue);
  }
}
