package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateResponseMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdatePersonalInformationRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.validation.PersonalInformationUpdateYamlValidator;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.port.in.UpdatePersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

@RestController
@RequestMapping("/api/v1/personal-information")
public class UpdatePersonalInformationController {

    private final UpdatePersonalInformationUseCase updatePersonalInformationUseCase;
    private final PersonalInformationUpdateWebMapper requestMapper;
    private final PersonalInformationUpdateResponseMapper responseMapper;
    private final PersonalInformationUpdateYamlValidator validator;
    private final CurrentPortalAccessContextResolver currentPortalAccessContextResolver;

    @Autowired
    public UpdatePersonalInformationController(
            UpdatePersonalInformationUseCase updatePersonalInformationUseCase,
            PersonalInformationUpdateWebMapper requestMapper,
            PersonalInformationUpdateResponseMapper responseMapper,
            PersonalInformationUpdateYamlValidator validator,
            CurrentPortalAccessContextResolver currentPortalAccessContextResolver) {
        this.updatePersonalInformationUseCase = updatePersonalInformationUseCase;
        this.requestMapper = requestMapper;
        this.responseMapper = responseMapper;
        this.validator = validator;
        this.currentPortalAccessContextResolver = currentPortalAccessContextResolver;
    }

    @PutMapping
    public Mono<MutationResponse<List<PersonalInformationUpdateResultResponse>>> update(
            @RequestBody Mono<UpdatePersonalInformationRequest> request,
            @RequestHeader(value = RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, required = false) String acceptLanguage) {
        return Mono.deferContextual(contextView -> currentPortalAccessContextResolver.current()
                .flatMap(portalAccessContext -> request.flatMap(body -> {
                    String resolvedLocale = resolveLanguage(contextView, acceptLanguage);
                    List<ApiError> errors = validator == null
                            ? List.of()
                            : validator.validate(body, resolvedLocale, accountEnv(portalAccessContext),
                                    trustCode(portalAccessContext), schemeType(portalAccessContext));
                    if (!errors.isEmpty()) {
                        return Mono.just(MutationResponse.failure(ApiStatus.VALIDATION_FAILED, errors));
                    }
                    return Mono.just(requestMapper.toCommand(body.applyToAllAccounts(), body))
                            .flatMap(command -> updatePersonalInformationUseCase.execute(command))
                            .map(responseMapper::toResponse);
                })));
    }

    private String resolveLanguage(ContextView contextView, String acceptLanguage) {
        RequestHeaderContext context = contextView.getOrDefault(RequestHeaderContextKeys.CONTEXT_KEY, null);
        return RequestLanguageResolver.resolve(context, acceptLanguage, context == null ? null : context.language());
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
}
