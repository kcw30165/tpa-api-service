package com.bct.ngtpa.apiservice.adapter.in.web.response;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET personal-information response: base envelope + page/form only.
 */
public record PersonalInformationResponse(
        boolean success,
        String status,
        Map<String, Object> page,
        Map<String, Object> form,
        List<ApiMessage> messages,
        List<ApiError> errors) {

    public PersonalInformationResponse {
        page = page == null ? Map.of() : new LinkedHashMap<>(page);
        form = form == null ? Map.of() : new LinkedHashMap<>(form);
        messages = messages == null ? List.of() : List.copyOf(messages);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    @SuppressWarnings("unchecked")
    public static PersonalInformationResponse success(Map<String, Object> mappedPageAndForm) {
        Map<String, Object> page = mappedPageAndForm == null ? Map.of() : (Map<String, Object>) mappedPageAndForm.get("page");
        Map<String, Object> form = mappedPageAndForm == null ? Map.of() : (Map<String, Object>) mappedPageAndForm.get("form");
        return new PersonalInformationResponse(true, ApiStatus.SUCCESS.toString(), page, form, List.of(), List.of());
    }
}
