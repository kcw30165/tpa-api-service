package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.shared.config.ConfigVariantCandidateGenerator;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfiguredErrorMessageResolverTest {

    @Test
    void resolvesExactBaseMessage() {
        var resolver = resolverWith(Map.of(
                "error-message.en.err.request.invalid", "Invalid request."));

        assertEquals(
                "Invalid request.",
                resolver.resolve(ErrorCodes.REQUEST_INVALID, "en", "JP", "JPM", "OE"));
    }

    @Test
    void resolvesExactVariantSpecificMessage() {
        var resolver = resolverWith(Map.of(
                "error-message.en.err.member.context.unavailable.JP.JPM.OE",
                "Member context is unavailable for this scheme. Please try again later."));

        assertEquals(
                "Member context is unavailable for this scheme. Please try again later.",
                resolver.resolve(ErrorCodes.MEMBER_CONTEXT_UNAVAILABLE, "en", "JP", "JPM", "OE"));
    }

    @Test
    void fallsBackUsingSharedVariantCandidateOrder() {
        var resolver = resolverWith(Map.of(
                "error-message.en.err.member.context.unavailable.JP.OE", "env-scheme",
                "error-message.en.err.member.context.unavailable.JP.JPM", "env-trust",
                "error-message.en.err.member.context.unavailable.JPM.OE", "trust-scheme"));

        assertEquals(
                "env-scheme",
                resolver.resolve(ErrorCodes.MEMBER_CONTEXT_UNAVAILABLE, "en", "JP", "JPM", "OE"));
    }

    @Test
    void fallsBackFromRequestedLocaleToEnglish() {
        var resolver = resolverWith(Map.of(
                "error-message.en.err.apim.service.unavailable.JP",
                "Service is temporarily unavailable in JP environment. Please try again later."));

        assertEquals(
                "Service is temporarily unavailable in JP environment. Please try again later.",
                resolver.resolve(ErrorCodes.APIM_SERVICE_UNAVAILABLE, "zh_HK", "JP", null, null));
    }

    @Test
    void normalizesZhHkLocaleInput() {
        var resolver = resolverWith(Map.of(
                "error-message.zh_HK.err.request.validation.failed", "請求內容無效。"));

        assertEquals(
                "請求內容無效。",
                resolver.resolve(ErrorCodes.REQUEST_VALIDATION_FAILED, "zh-hk", null, null, null));
    }

    @Test
    void fallsBackToEnglishForBlankNullAndUnknownLocales() {
        var resolver = resolverWith(Map.of(
                "error-message.en.err.request.body.malformed", "Malformed request body."));

        assertEquals(
                "Malformed request body.",
                resolver.resolve(ErrorCodes.REQUEST_BODY_MALFORMED, "   ", null, null, null));
        assertEquals(
                "Malformed request body.",
                resolver.resolve(ErrorCodes.REQUEST_BODY_MALFORMED, null, null, null, null));
        assertEquals(
                "Malformed request body.",
                resolver.resolve(ErrorCodes.REQUEST_BODY_MALFORMED, "fr", null, null, null));
    }

    @Test
    void fallsBackToConfiguredSystemUnexpectedMessage() {
        var resolver = resolverWith(Map.of(
                "error-message.en.err.system.unexpected",
                "Sorry, this service might be interrupted. Please try again later."));

        assertEquals(
                "Sorry, this service might be interrupted. Please try again later.",
                resolver.resolve("err.unknown.code", "en", null, null, null));
    }

    @Test
    void fallsBackToHardcodedMessageWhenConfigIsMissing() {
        var resolver = resolverWith(Map.of());

        assertEquals(
                ConfiguredErrorMessageResolver.HARD_CODED_FALLBACK,
                resolver.resolve("err.unknown.code", "en", null, null, null));
    }

    @Test
    void handlesBlankErrorCodeUsingSafeFallbacks() {
        var resolver = resolverWith(Map.of(
                "error-message.en.err.system.unexpected",
                "Sorry, this service might be interrupted. Please try again later."));

        assertEquals(
                "Sorry, this service might be interrupted. Please try again later.",
                resolver.resolve("   ", "en", null, null, null));
    }

    private static ConfiguredErrorMessageResolver resolverWith(Map<String, String> properties) {
        var environment = new MockEnvironment();
        properties.forEach(environment::setProperty);

        var configResolver = new DefaultConfigVariantResolver(
                java.util.List.of(new ErrorMessageConfigSource(environment)),
                java.util.List.of(new DefaultConfigKeyCandidateStrategy(new ConfigVariantCandidateGenerator())));

        return new ConfiguredErrorMessageResolver(configResolver);
    }
}