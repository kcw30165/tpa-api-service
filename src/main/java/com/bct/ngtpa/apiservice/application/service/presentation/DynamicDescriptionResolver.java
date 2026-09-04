package com.bct.ngtpa.apiservice.application.service.presentation;

public final class DynamicDescriptionResolver {
  public String resolve(String language, String english, String traditionalChinese) {
    boolean zh = isZh(language);
    String selected = zh ? traditionalChinese : english;
    String fallback = zh ? english : traditionalChinese;
    if (hasText(selected)) return selected.trim();
    if (hasText(fallback)) return fallback.trim();
    throw new IllegalArgumentException("at least one description is required");
  }

  private static boolean isZh(String value) {
    if (value == null) return false;
    String tag = value.trim().replace('_', '-');
    return tag.equalsIgnoreCase("zh") || tag.equalsIgnoreCase("zh-HK");
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
