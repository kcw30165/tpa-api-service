package com.bct.ngtpa.apiservice.application.service.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DynamicDescriptionResolverPreservationTest {
  private final DynamicDescriptionResolver resolver = new DynamicDescriptionResolver();

  @Test
  void preservesEnglishAndTraditionalChineseTextExactly() {
    assertThat(resolver.resolve("en", "  English name  ", "  中文名稱  "))
        .isEqualTo("  English name  ");
    assertThat(resolver.resolve("zh-HK", "  English name  ", "  中文名稱  ")).isEqualTo("  中文名稱  ");
    assertThat(resolver.resolve("zh_HK", "English", "中文")).isEqualTo("中文");
  }

  @Test
  void preservesFallbackAndDefaultsUnsupportedLanguageToEnglish() {
    assertThat(resolver.resolve("zh-HK", "  fallback  ", " ")).isEqualTo("  fallback  ");
    assertThat(resolver.resolve("fr", "  English  ", "中文")).isEqualTo("  English  ");
  }

  @Test
  void rejectsWhenBothDescriptionsAreBlank() {
    assertThatThrownBy(() -> resolver.resolve("en", " ", null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
