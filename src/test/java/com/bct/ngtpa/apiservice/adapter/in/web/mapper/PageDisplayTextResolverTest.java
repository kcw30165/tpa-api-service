package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.shared.config.ConfigVariantCandidateGenerator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PageDisplayTextResolverTest {

    private final PageDisplayTextResolver resolver =
            new PageDisplayTextResolver(new ConfigVariantCandidateGenerator());

    @Test
    void resolvesMostSpecificDisplayValue() {
        var display = Map.of("en", Map.of(
                "personalInformation.section.addressInformation.title", "Address Information",
                "personalInformation.section.addressInformation.title.PROD.RM.MPF", "MPF Address Information"));

        assertThat(resolver.resolve(
                display,
                "personalInformation.section.addressInformation.title",
                Map.of("en", "Inline Address Information"),
                "en",
                "PROD",
                "RM",
                "MPF"))
                .isEqualTo("MPF Address Information");
    }

    @Test
    void fallsBackThroughVariantCandidatesBeforeBaseCode() {
        var display = Map.of("en", Map.of(
                "personalInformation.field.emailAddress.label", "Email Address",
                "personalInformation.field.emailAddress.label.PROD.RM", "RM Email Address"));

        assertThat(resolver.resolve(
                display,
                "personalInformation.field.emailAddress.label",
                Map.of("en", "Inline Email Address"),
                "en",
                "PROD",
                "RM",
                "MPF"))
                .isEqualTo("RM Email Address");
    }

    @Test
    void fallsBackToBaseDisplayCode() {
        var display = Map.of("en", Map.of(
                "personalInformation.field.emailAddress.placeholder", "Enter email address"));

        assertThat(resolver.resolve(
                display,
                "personalInformation.field.emailAddress.placeholder",
                Map.of("en", "Inline placeholder"),
                "en",
                "PROD",
                "RM",
                "MPF"))
                .isEqualTo("Enter email address");
    }

    @Test
    void fallsBackToEnglishDisplayWhenRequestedLanguageMissing() {
        var display = Map.of("en", Map.of(
                "personalInformation.page.title.PROD.RM.MPF", "MPF Personal Information"));

        assertThat(resolver.resolve(
                display,
                "personalInformation.page.title",
                Map.of("zh_HK", "Inline Chinese Title"),
                "zh_HK",
                "PROD",
                "RM",
                "MPF"))
                .isEqualTo("MPF Personal Information");
    }

    @Test
    void fallsBackToInlineLocalizedTextWhenDisplayCodeMissing() {
        assertThat(resolver.resolve(
                Map.of(),
                "personalInformation.action.submit.label",
                Map.of("en", "Submit", "zh_HK", "提交"),
                "zh-HK",
                "PROD",
                "RM",
                "MPF"))
                .isEqualTo("提交");
    }

    @Test
    void returnsEmptyStringWhenNoDisplayOrInlineFallbackExists() {
        assertThat(resolver.resolve(
                Map.of(),
                "personalInformation.action.submit.label",
                Map.of(),
                "en",
                "PROD",
                "RM",
                "MPF"))
                .isEmpty();
    }
}
