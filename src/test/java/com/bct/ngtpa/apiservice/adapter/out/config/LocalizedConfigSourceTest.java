package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.shared.config.ConfigCategory;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LocalizedConfigSourceTest {

    @Test
    void categoryReturnsConfiguredCategory() {
        var source = new LocalizedConfigSource(
                ConfigCategory.ERROR_MESSAGE,
                new LocalizedConfigProperties());

        assertThat(source.category()).isEqualTo(ConfigCategory.ERROR_MESSAGE);
    }

    @Test
    void getReturnsConfiguredEnglishValue() {
        var properties = new LocalizedConfigProperties();
        properties.put("en", Map.of("err.request.invalid", "Invalid request."));
        var source = new LocalizedConfigSource(ConfigCategory.ERROR_MESSAGE, properties);

        assertThat(source.get("err.request.invalid", Locale.ENGLISH))
                .contains("Invalid request.");
    }

    @Test
    void getReturnsConfiguredTraditionalChineseValue() {
        var properties = new LocalizedConfigProperties();
        properties.put("zh_HK", Map.of("err.request.invalid", "請求無效。"));
        var source = new LocalizedConfigSource(ConfigCategory.ERROR_MESSAGE, properties);

        assertThat(source.get("err.request.invalid", Locale.forLanguageTag("zh-HK")))
                .contains("請求無效。");
    }

    @Test
    void getFallsBackToEnglishWhenRequestedLocaleMissing() {
        var properties = new LocalizedConfigProperties();
        properties.put("en", Map.of("err.request.invalid", "Invalid request."));
        var source = new LocalizedConfigSource(ConfigCategory.ERROR_MESSAGE, properties);

        assertThat(source.get("err.request.invalid", Locale.forLanguageTag("zh-HK")))
                .contains("Invalid request.");
    }

    @Test
    void getUsesEnglishLocaleWhenLocaleIsNull() {
        var properties = new LocalizedConfigProperties();
        properties.put("en", Map.of("err.request.invalid", "Invalid request."));
        var source = new LocalizedConfigSource(ConfigCategory.ERROR_MESSAGE, properties);

        assertThat(source.get("err.request.invalid", null))
                .contains("Invalid request.");
    }

    @Test
    void getReturnsEmptyForBlankKey() {
        var properties = new LocalizedConfigProperties();
        properties.put("en", Map.of("err.request.invalid", "Invalid request."));
        var source = new LocalizedConfigSource(ConfigCategory.ERROR_MESSAGE, properties);

        assertThat(source.get(" ", Locale.ENGLISH))
                .isEmpty();
    }

    @Test
    void getReturnsEmptyWhenValueIsNotConfigured() {
        var properties = new LocalizedConfigProperties();
        properties.put("en", Map.of("err.request.invalid", "Invalid request."));
        var source = new LocalizedConfigSource(ConfigCategory.ERROR_MESSAGE, properties);

        assertThat(source.get("err.system.unexpected", Locale.ENGLISH))
                .isEmpty();
    }

    @Test
    void baseCodeSourceRejectsUnrelatedKeys() {
        var properties = new LocalizedConfigProperties();
        properties.put("en", Map.of("date", "dd/MM/yyyy"));
        var source = new LocalizedConfigSource(ConfigCategory.DISPLAY_DATE_FORMAT, properties, "date");

        assertThat(source.get("amount", Locale.ENGLISH)).isEmpty();
    }

    @Test
    void baseCodeSourceSupportsWildcardFallbackForBaseKey() {
        var properties = new LocalizedConfigProperties();
        properties.put("en", Map.of("*", "dd/MM/yyyy"));
        var source = new LocalizedConfigSource(ConfigCategory.DISPLAY_DATE_FORMAT, properties, "date");

        assertThat(source.get("date", Locale.ENGLISH)).contains("dd/MM/yyyy");
    }

    @Test
    void baseCodeSourceSupportsLegacyVariantKeyFallback() {
        var properties = new LocalizedConfigProperties();
        properties.put("en", Map.of("PROD.RM", "dd/MM/yyyy"));
        var source = new LocalizedConfigSource(ConfigCategory.DISPLAY_DATE_FORMAT, properties, "date");

        assertThat(source.get("date.PROD.RM", Locale.ENGLISH)).contains("dd/MM/yyyy");
    }
}
