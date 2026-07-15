package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.registration;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PageDisplayTextResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ActionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageSchemaProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.response.RegistrationCancellationPageActionResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.RegistrationCancellationPageResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Resolves the standalone registration-cancelled page. No variant is used. */
public final class RegistrationCancellationPageMapper {
    public static final String PAGE_KEY = "registration-cancelled";
    private static final String BODY_CODE = "registration.cancelled.body";

    private final BffPagesProperties pages;
    private final PageDisplayTextResolver displayTextResolver;

    public RegistrationCancellationPageMapper(
            BffPagesProperties pages,
            PageDisplayTextResolver displayTextResolver) {
        this.pages = Objects.requireNonNull(pages, "pages must not be null");
        this.displayTextResolver = Objects.requireNonNull(
                displayTextResolver, "displayTextResolver must not be null");
    }

    public RegistrationCancellationPageResponse map(String language) {
        PageSchemaProperties page = requirePage();
        if (page.getVariants() != null && !page.getVariants().isEmpty()) {
            throw new IllegalStateException("registration-cancelled page must not define variants");
        }
        String pageId = page.getMetadata() == null
                ? PAGE_KEY
                : required(page.getMetadata().getId(), "metadata.id");
        String title = page.getMetadata() == null
                ? ""
                : displayTextResolver.resolve(
                        page.getDisplay(),
                        page.getMetadata().getTitleCode(),
                        page.getMetadata().getTitle(),
                        language,
                        null,
                        null,
                        null);
        String body = displayTextResolver.resolve(
                page.getDisplay(), BODY_CODE, Map.of(), language, null, null, null);
        List<RegistrationCancellationPageActionResponse> actions = page.getForm() == null
                        || page.getForm().getActions() == null
                ? List.of()
                : page.getForm().getActions().stream().map(action -> mapAction(page, action, language)).toList();
        if (title == null || title.isBlank() || body == null || body.isBlank() || actions.isEmpty()) {
            throw new IllegalStateException("registration-cancelled page content is incomplete");
        }
        return new RegistrationCancellationPageResponse(pageId, title, body, actions);
    }

    private RegistrationCancellationPageActionResponse mapAction(
            PageSchemaProperties page,
            ActionProperties action,
            String language) {
        if (action == null) {
            throw new IllegalStateException("registration-cancelled page contains a null action");
        }
        String id = required(firstText(action.getId(), action.getName()), "action.id");
        String label = displayTextResolver.resolve(
                page.getDisplay(),
                action.getLabelCode(),
                action.getLabel(),
                language,
                null,
                null,
                null);
        return new RegistrationCancellationPageActionResponse(
                id,
                required(action.getType(), "action.type"),
                required(label, "action.label"),
                required(action.getNextStep(), "action.nextStep"));
    }

    private PageSchemaProperties requirePage() {
        PageSchemaProperties page = pages.getPages() == null ? null : pages.getPages().get(PAGE_KEY);
        if (page == null) {
            throw new IllegalStateException("registration-cancelled page is missing");
        }
        return page;
    }

    private String firstText(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("registration-cancelled " + field + " is required");
        }
        return value.trim();
    }
}
