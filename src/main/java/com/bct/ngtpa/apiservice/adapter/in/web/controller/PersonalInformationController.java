package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.response.FormPageResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.FormSchemaResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.in.GetPersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextResolver;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

@RestController
@RequestMapping("/api/v1/personal-information")
public class PersonalInformationController {

    private final GetPersonalInformationUseCase getPersonalInformationUseCase;
    private final PersonalInformationWebMapper personalInformationWebMapper;
    private final PortalAccessContextResolver portalAccessContextResolver;

    @Autowired
    public PersonalInformationController(
            GetPersonalInformationUseCase getPersonalInformationUseCase,
            PersonalInformationWebMapper personalInformationWebMapper,
            PortalAccessContextResolver portalAccessContextResolver) {
        this.getPersonalInformationUseCase = getPersonalInformationUseCase;
        this.personalInformationWebMapper = personalInformationWebMapper;
        this.portalAccessContextResolver = portalAccessContextResolver;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<FormPageResponse<FormSchemaResponse>> getPersonalInformation(
            @RequestHeader(value = RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, required = false) String acceptLanguage) {
        return Mono.deferContextual(contextView -> {
            String language = resolveLanguage(contextView, acceptLanguage);
            return portalAccessContextResolver.current()
                    .flatMap(portalAccessContext -> {
                        requireAccountRef(portalAccessContext);
                        return getPersonalInformationUseCase
                                .execute(new GetPersonalInformationCommand(language))
                                .map(result -> personalInformationWebMapper.toFormPageResponse(
                                        result,
                                        language,
                                        accountEnv(portalAccessContext),
                                        trustCode(portalAccessContext),
                                        schemeType(portalAccessContext)));
                    });
        });
    }

    private String requireAccountRef(PortalAccessContext context) {
        if (context == null || context.account() == null || !StringUtils.hasText(context.account().accountRef())) {
            throw new PortalAccessContextResolutionException(
                    ErrorCodes.MEMBER_CONTEXT_INVALID,
                    "Missing Account-Ref header for selected-account API");
        }
        return context.account().accountRef();
    }

    private String accountEnv(PortalAccessContext context) {
        return context == null || context.account() == null ? null : context.account().accountEnv();
    }

    private String trustCode(PortalAccessContext context) {
        return context == null || context.account() == null ? null : context.account().trustCode();
    }

    private String schemeType(PortalAccessContext context) {
        return context == null || context.account() == null ? null : context.account().schemeType();
    }

    private String resolveLanguage(ContextView contextView, String acceptLanguage) {
        RequestHeaderContext requestHeaderContext = contextView.getOrDefault(
                RequestHeaderContextKeys.CONTEXT_KEY,
                null);
        return RequestLanguageResolver.resolve(requestHeaderContext, acceptLanguage, null);
    }
}
