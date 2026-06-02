package com.bct.ngtpa.apiservice.adapter.in.web;

import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import com.bct.ngtpa.apiservice.application.dto.GetReferenceDataCountriesCommand;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDataCountriesResult;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.in.GetReferenceDataCountriesUseCase;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

@RestController
@RequestMapping("/api/v1/reference-data")
@RequiredArgsConstructor
public class ReferenceDataController {

    private final GetReferenceDataCountriesUseCase getReferenceDataCountriesUseCase;

    @GetMapping(value = "/countries", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ReferenceDataCountriesResult> getCountries(
            @RequestHeader(value = RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, required = false) String acceptLanguage) {
        return Mono.deferContextual(contextView -> {
            String accountRef = resolveRequiredAccountRef(contextView);
            String language = resolveLanguage(contextView, acceptLanguage);
            return getReferenceDataCountriesUseCase.execute(new GetReferenceDataCountriesCommand(accountRef, language));
        });
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
