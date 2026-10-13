package com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class UsernameRecoveryRequestLanguageResolverTest {

  @Test
  void anAbsentOrBlankHeaderNamesNoLanguage() {
    assertThat(UsernameRecoveryRequestLanguageResolver.fromHeader(null)).isNull();
    assertThat(UsernameRecoveryRequestLanguageResolver.fromHeader("")).isNull();
    assertThat(UsernameRecoveryRequestLanguageResolver.fromHeader("   ")).isNull();
  }

  @Test
  void aSingleLanguageTagIsParsedAndTrimmed() {
    assertThat(UsernameRecoveryRequestLanguageResolver.fromHeader("zh-HK"))
        .isEqualTo(Locale.forLanguageTag("zh-HK"));
    assertThat(UsernameRecoveryRequestLanguageResolver.fromHeader(" en "))
        .isEqualTo(Locale.ENGLISH);
  }

  @Test
  void aValueWithoutALanguageNamesNoLanguage() {
    assertThat(UsernameRecoveryRequestLanguageResolver.fromHeader("*")).isNull();
    assertThat(UsernameRecoveryRequestLanguageResolver.fromHeader("!!!")).isNull();
  }
}
