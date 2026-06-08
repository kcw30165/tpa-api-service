package com.bct.ngtpa.apiservice.adapter.in.web.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import org.junit.jupiter.api.Test;

class RequestLanguageResolverTest {

    @Test
    void rawAcceptLanguagePrefersHeaderContextLanguageWhenPresent() {
        var context = new RequestHeaderContext("ACC-123", "REQ-1", "zh-HK");

        assertThat(RequestLanguageResolver.resolve(context, "en-US,en;q=0.9", "en"))
                .isEqualTo("zh_HK");
    }

    @Test
    void rawAcceptLanguageFallsBackToRawWhenContextMissing() {
        assertThat(RequestLanguageResolver.resolve(null, "en-US,en;q=0.9", "zh_HK"))
                .isEqualTo("en");
        assertThat(RequestLanguageResolver.resolve(null, "zh", "en"))
                .isEqualTo("zh_HK");
    }

    @Test
    void missingAcceptLanguageDefaultsToEnglishAndIgnoresFallbackLanguage() {
        assertThat(RequestLanguageResolver.resolve(null, " ", "zh-HK")).isEqualTo("en");
        assertThat(RequestLanguageResolver.resolve(null, null, "zh_HK")).isEqualTo("en");
        assertThat(RequestLanguageResolver.resolve(null, null, "fr-FR")).isEqualTo("en");
        assertThat(RequestLanguageResolver.resolve(null, null, null)).isEqualTo("en");
    }

    @Test
    void isZhHkNormalizesInput() {
        assertThat(RequestLanguageResolver.isZhHk("zh")).isTrue();
        assertThat(RequestLanguageResolver.isZhHk("zh_HK")).isTrue();
        assertThat(RequestLanguageResolver.isZhHk("en-HK")).isFalse();
        assertThat(RequestLanguageResolver.isZhHk(" ")).isFalse();
    }
}
