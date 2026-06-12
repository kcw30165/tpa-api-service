package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.shared.config.ConfigVariantCandidateGenerator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PageDisplayTextResolverFlatVariantTest {

    private static final String PO_BOX_MESSAGE_CODE = "personalInformation.address.poBoxBlocked.message";

    private final PageDisplayTextResolver resolver = new PageDisplayTextResolver(new ConfigVariantCandidateGenerator());

    @Test
    void accountEnvSuffixVariantWinsWhileCallerKeepsBaseMessageCode() {
        Map<String, Map<String, String>> display = new LinkedHashMap<>();
        display.put(PO_BOX_MESSAGE_CODE, Map.of(
                "en", "Base PO Box message",
                "zh_HK", "Base PO Box message zh"));
        display.put(PO_BOX_MESSAGE_CODE + ".JP", Map.of(
                "en", "Japan PO Box message",
                "zh_HK", "Japan PO Box message zh"));

        String resolved = resolver.resolve(
                display,
                PO_BOX_MESSAGE_CODE,
                Map.of("en", "Inline fallback must not win"),
                "en",
                "JP",
                null,
                null);

        assertThat(resolved).isEqualTo("Japan PO Box message");
    }

    @Test
    void fallsBackToBaseDisplayCodeWhenAccountEnvVariantIsMissing() {
        Map<String, Map<String, String>> display = new LinkedHashMap<>();
        display.put(PO_BOX_MESSAGE_CODE, Map.of(
                "en", "Base PO Box message",
                "zh_HK", "Base PO Box message zh"));

        String resolved = resolver.resolve(
                display,
                PO_BOX_MESSAGE_CODE,
                Map.of("en", "Inline fallback must not win"),
                "en",
                "HK",
                null,
                null);

        assertThat(resolved).isEqualTo("Base PO Box message");
    }

    @Test
    void accountEnvSuffixVariantUsesRequestedLanguage() {
        Map<String, Map<String, String>> display = new LinkedHashMap<>();
        display.put(PO_BOX_MESSAGE_CODE, Map.of(
                "en", "Base PO Box message",
                "zh_HK", "Base PO Box message zh"));
        display.put(PO_BOX_MESSAGE_CODE + ".JP", Map.of(
                "en", "Japan PO Box message",
                "zh_HK", "Japan PO Box message zh"));

        String resolved = resolver.resolve(
                display,
                PO_BOX_MESSAGE_CODE,
                Map.of("zh_HK", "Inline fallback must not win"),
                "zh_HK",
                "JP",
                null,
                null);

        assertThat(resolved).isEqualTo("Japan PO Box message zh");
    }
}
