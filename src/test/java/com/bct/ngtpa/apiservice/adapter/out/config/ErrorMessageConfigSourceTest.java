package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.shared.config.ConfigCategory;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorMessageConfigSourceTest {

    @Test
    void categoryIsErrorMessage() {
        var source = new ErrorMessageConfigSource(new ErrorMessageProperties());

        assertThat(source.category()).isEqualTo(ConfigCategory.ERROR_MESSAGE);
    }

    @Test
    void getReturnsConfiguredEnglishMessage() {
        var properties = new ErrorMessageProperties();
        properties.put("en", Map.of("err.request.invalid", "Invalid request."));
        var source = new ErrorMessageConfigSource(properties);

        assertThat(source.get("err.request.invalid", Locale.ENGLISH))
                .contains("Invalid request.");
    }

    @Test
    void getReturnsConfiguredTraditionalChineseMessage() {
        var properties = new ErrorMessageProperties();
        properties.put("zh_HK", Map.of("err.request.invalid", "請求無效。"));
        var source = new ErrorMessageConfigSource(properties);

        assertThat(source.get("err.request.invalid", Locale.forLanguageTag("zh-HK")))
                .contains("請求無效。");
    }

    @Test
    void getUsesEnglishLocaleWhenLocaleIsNull() {
        var properties = new ErrorMessageProperties();
        properties.put("en", Map.of("err.request.invalid", "Invalid request."));
        var source = new ErrorMessageConfigSource(properties);

        assertThat(source.get("err.request.invalid", null))
                .contains("Invalid request.");
    }

    @Test
    void getReturnsEmptyForBlankKey() {
        var properties = new ErrorMessageProperties();
        properties.put("en", Map.of("err.request.invalid", "Invalid request."));
        var source = new ErrorMessageConfigSource(properties);

        assertThat(source.get(" ", Locale.ENGLISH))
                .isEmpty();
    }

    @Test
    void getReturnsEmptyWhenMessageIsNotConfigured() {
        var properties = new ErrorMessageProperties();
        properties.put("en", Map.of("err.request.invalid", "Invalid request."));
        var source = new ErrorMessageConfigSource(properties);

        assertThat(source.get("err.system.unexpected", Locale.ENGLISH))
                .isEmpty();
    }
}
