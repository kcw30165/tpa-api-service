package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.shared.config.ConfigLookupRequest;
import com.bct.ngtpa.apiservice.shared.config.ConfigResolutionException;
import com.bct.ngtpa.apiservice.shared.config.ConfigVariantResolver;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigBackedErrorMessageResolverTest {

    @Test
    void resolveReturnsRequestedLocaleMessage() {
        var resolver = new ConfigBackedErrorMessageResolver(new StubConfigVariantResolver(Map.of(
                key("zh_HK", ErrorCodes.APIM_SERVICE_UNAVAILABLE), "服務暫時不可用。",
                key("en", ErrorCodes.APIM_SERVICE_UNAVAILABLE), "Service is temporarily unavailable.")));

        var message = resolver.resolve(
                ErrorCodes.APIM_SERVICE_UNAVAILABLE,
                "zh-HK",
                "JP",
                "JPM",
                "OE");

        assertThat(message).isEqualTo("服務暫時不可用。");
    }

    @Test
    void resolveFallsBackToEnglishWhenRequestedLocaleHasNoMessage() {
        var resolver = new ConfigBackedErrorMessageResolver(new StubConfigVariantResolver(Map.of(
                key("en", ErrorCodes.APIM_SERVICE_UNAVAILABLE), "Service is temporarily unavailable.")));

        var message = resolver.resolve(
                ErrorCodes.APIM_SERVICE_UNAVAILABLE,
                "zh-HK",
                "JP",
                "JPM",
                "OE");

        assertThat(message).isEqualTo("Service is temporarily unavailable.");
    }

    @Test
    void resolveFallsBackToConfiguredSystemUnexpectedWhenRequestedCodeIsMissing() {
        var resolver = new ConfigBackedErrorMessageResolver(new StubConfigVariantResolver(Map.of(
                key("en", ErrorCodes.SYSTEM_UNEXPECTED), "Sorry, this service might be interrupted. Please try again later.")));

        var message = resolver.resolve(
                ErrorCodes.APIM_SERVICE_UNAVAILABLE,
                "en",
                "JP",
                "JPM",
                "OE");

        assertThat(message).isEqualTo("Sorry, this service might be interrupted. Please try again later.");
    }

    @Test
    void resolveFallsBackToConfiguredSystemUnexpectedWhenErrorCodeIsBlank() {
        var resolver = new ConfigBackedErrorMessageResolver(new StubConfigVariantResolver(Map.of(
                key("en", ErrorCodes.SYSTEM_UNEXPECTED), "Configured fallback.")));

        var message = resolver.resolve(" ", "zh-HK", "JP", "JPM", "OE");

        assertThat(message).isEqualTo("Configured fallback.");
    }

    @Test
    void resolveFallsBackToHardCodedMessageWhenConfigResolverThrows() {
        var resolver = new ConfigBackedErrorMessageResolver(request -> {
            throw new ConfigResolutionException("boom");
        });

        var message = resolver.resolve(
                ErrorCodes.APIM_SERVICE_UNAVAILABLE,
                "zh-HK",
                "JP",
                "JPM",
                "OE");

        assertThat(message).isEqualTo(ConfigBackedErrorMessageResolver.HARD_CODED_FALLBACK);
    }

    @Test
    void unsupportedLocaleIsNormalizedToEnglish() {
        var resolver = new ConfigBackedErrorMessageResolver(new StubConfigVariantResolver(Map.of(
                key("en", ErrorCodes.REQUEST_INVALID), "Invalid request.")));

        var message = resolver.resolve(ErrorCodes.REQUEST_INVALID, "fr-FR", null, null, null);

        assertThat(message).isEqualTo("Invalid request.");
    }

    private static String key(String locale, String code) {
        return locale + "|" + code;
    }

    private static final class StubConfigVariantResolver implements ConfigVariantResolver {

        private final Map<String, String> values;

        private StubConfigVariantResolver(Map<String, String> values) {
            this.values = new LinkedHashMap<>(values);
        }

        @Override
        public Optional<String> resolve(ConfigLookupRequest request) {
            Locale locale = request.context().locale();
            var localeKey = locale == null ? "en" : locale.toString();
            return Optional.ofNullable(values.get(key(localeKey, request.code())));
        }
    }
}

