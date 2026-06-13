package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateResponseMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdatePersonalInformationRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.validation.PersonalInformationUpdateYamlValidator;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.in.UpdatePersonalInformationUseCase;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/personal-information")
public class UpdatePersonalInformationController {

    private final UpdatePersonalInformationUseCase updatePersonalInformationUseCase;
    private final PersonalInformationUpdateWebMapper requestMapper;
    private final PersonalInformationUpdateResponseMapper responseMapper;
    private final PersonalInformationUpdateYamlValidator validator;

    @Autowired
    public UpdatePersonalInformationController(
            UpdatePersonalInformationUseCase updatePersonalInformationUseCase,
            PersonalInformationUpdateWebMapper requestMapper,
            PersonalInformationUpdateResponseMapper responseMapper,
            PersonalInformationUpdateYamlValidator validator) {
        this.updatePersonalInformationUseCase = updatePersonalInformationUseCase;
        this.requestMapper = requestMapper;
        this.responseMapper = responseMapper;
        this.validator = validator;
    }

    // Kept for existing focused unit tests that construct the controller directly with mocked mappers/use case.
    public UpdatePersonalInformationController(
            UpdatePersonalInformationUseCase updatePersonalInformationUseCase,
            PersonalInformationUpdateWebMapper requestMapper,
            PersonalInformationUpdateResponseMapper responseMapper) {
        this(updatePersonalInformationUseCase, requestMapper, responseMapper, null);
    }

    @PutMapping
    public Mono<MutationResponse<PersonalInformationUpdateResultResponse>> update(
            @RequestBody Mono<UpdatePersonalInformationRequest> request) {
        return Mono.deferContextual(contextView -> {
            RequestHeaderContext context = contextView.getOrDefault(RequestHeaderContextKeys.CONTEXT_KEY, null);
            String accountRef = context == null ? null : context.accountRef();
            if (accountRef == null || accountRef.isBlank()) {
                return Mono.error(new PortalAccessContextResolutionException(
                        ErrorCodes.MEMBER_CONTEXT_INVALID,
                        "Account-Ref is required for personal information update."));
            }
            return request.flatMap(body -> {
                List<ApiError> errors = validator == null
                        ? List.of()
                        : validator.validate(body, context.language(), null, null, null);
                if (!errors.isEmpty()) {
                    return Mono.just(MutationResponse.failure(ApiStatus.VALIDATION_FAILED, errors));
                }
                return Mono.just(requestMapper.toCommand(accountRef, body.applyToAllAccounts(), body))
                        .flatMap(command -> updatePersonalInformationUseCase.execute(command))
                        .map(responseMapper::toResponse);
            });
        });
    }
}
