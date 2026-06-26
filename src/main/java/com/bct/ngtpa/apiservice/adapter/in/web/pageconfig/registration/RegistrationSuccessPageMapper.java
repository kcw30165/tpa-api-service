package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.registration;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ActionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageSchemaProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageVariantProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.response.RegistrationSuccessPageActionResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.RegistrationSuccessPageResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
public class RegistrationSuccessPageMapper {

    private static final String REGISTRATION_SUCCESS_PAGE_ID = "registration-success";
    private static final String DEFAULT_LANGUAGE = "en";

    private final BffPagesProperties properties;

    public RegistrationSuccessPageMapper(BffPagesProperties properties) {
        this.properties = properties;
    }

    public RegistrationSuccessPageResponse map(String pageId, String variant, String language) {
        PageSchemaProperties page = requirePage(pageId);
        PageVariantProperties pageVariant = requireVariant(page, variant);
        String resolvedLanguage = RequestLanguageResolver.normalize(language);

        return new RegistrationSuccessPageResponse(
                pageId,
                variant,
                requireLocalizedValue(pageVariant.getTitle(), resolvedLanguage, "Registration success page title is missing."),
                requireLocalizedValue(pageVariant.getBody(), resolvedLanguage, "Registration success page body is missing."),
                requireActions(pageVariant.getActions(), resolvedLanguage));
    }

    private PageSchemaProperties requirePage(String pageId) {
        if (!REGISTRATION_SUCCESS_PAGE_ID.equals(pageId)
                || properties == null
                || properties.getPages() == null
                || properties.getPages().get(pageId) == null) {
            throw new IllegalStateException("Registration success page configuration is missing.");
        }
        return properties.getPages().get(pageId);
    }

    private PageVariantProperties requireVariant(PageSchemaProperties page, String variant) {
        if (!StringUtils.hasText(variant)
                || page.getVariants() == null
                || page.getVariants().get(variant) == null) {
            throw new IllegalStateException("Registration success page variant is missing: " + variant);
        }
        return page.getVariants().get(variant);
    }

    private List<RegistrationSuccessPageActionResponse> requireActions(List<ActionProperties> actions, String language) {
        if (actions == null || actions.isEmpty()) {
            throw new IllegalStateException("Registration success page actions are missing.");
        }
        return actions.stream()
                .map(action -> new RegistrationSuccessPageActionResponse(
                        requireText(action.getId(), "Registration success page action id is missing."),
                        requireText(action.getType(), "Registration success page action type is missing."),
                        requireLocalizedValue(action.getLabel(), language, "Registration success page action label is missing."),
                        requireText(action.getNextStep(), "Registration success page action nextStep is missing.")))
                .toList();
    }

    private String requireLocalizedValue(Map<String, String> localizedValues, String language, String errorMessage) {
        if (localizedValues == null || localizedValues.isEmpty()) {
            throw new IllegalStateException(errorMessage);
        }

        for (String key : languageFallbackKeys(language)) {
            String value = localizedValues.get(key);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }

        return localizedValues.values().stream()
                .filter(StringUtils::hasText)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(errorMessage));
    }

    private List<String> languageFallbackKeys(String language) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        String normalized = RequestLanguageResolver.normalize(language);
        keys.add(normalized);
        keys.add(normalized.replace('-', '_'));
        keys.add(DEFAULT_LANGUAGE);
        return List.copyOf(keys);
    }

    private String requireText(String value, String errorMessage) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(errorMessage);
        }
        return value;
    }
}