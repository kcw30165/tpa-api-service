package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.response.FormPageResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PageResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.in.GetPersonalInformationUseCase;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PersonalInformationController {

    private final GetPersonalInformationUseCase getPersonalInformationUseCase;
    private final PersonalInformationWebMapper personalInformationWebMapper;

    @GetMapping(value = "/personal-information", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<FormPageResponse<Map<String, Object>>> getPersonalInformation(
            @RequestHeader(value = RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, required = false) String acceptLanguage) {

        return Mono.deferContextual(contextView -> {
            String accountRef = resolveRequiredAccountRef(contextView);
            String language = resolveLanguage(contextView, acceptLanguage);
            return getPersonalInformationUseCase.execute(new GetPersonalInformationCommand(accountRef, language))
                    .map(result -> personalInformationWebMapper.toResponse(result, language))
                    .map(this::toFormPageResponse);
        });
    }

    private FormPageResponse<Map<String, Object>> toFormPageResponse(Map<String, Object> mappedResponse) {
        return FormPageResponse.success(
                toPageResponse(mappedResponse != null ? mappedResponse.get("page") : null),
                toFormResponse(mappedResponse != null ? mappedResponse.get("form") : null));
    }

    private PageResponse toPageResponse(Object value) {
        if (!(value instanceof Map<?, ?> page)) {
            return new PageResponse(null, null, null);
        }
        return new PageResponse(
                stringValue(page.get("id")),
                stringValue(page.get("title")),
                stringValue(page.get("lang")));
    }

    private Map<String, Object> toFormResponse(Object value) {
        if (!(value instanceof Map<?, ?> form)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : form.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String resolveLanguage(ContextView contextView, String acceptLanguage) {
        RequestHeaderContext requestHeaderContext = contextView.getOrDefault(
                RequestHeaderContextKeys.CONTEXT_KEY,
                null);
        return RequestLanguageResolver.resolve(requestHeaderContext, acceptLanguage, null);
    }

    private String resolveRequiredAccountRef(ContextView contextView) {
        RequestHeaderContext requestHeaderContext = contextView.getOrDefault(
                RequestHeaderContextKeys.CONTEXT_KEY,
                null);
        if (requestHeaderContext == null || requestHeaderContext.accountRef() == null) {
            throw new PortalAccessContextResolutionException(
                    ErrorCodes.MEMBER_CONTEXT_INVALID,
                    "Missing Account-Ref header for selected-account API");
        }
        return requestHeaderContext.accountRef();
    }
}
