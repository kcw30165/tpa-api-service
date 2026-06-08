package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateResponseMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdatePersonalInformationRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateResultResponse;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.in.UpdatePersonalInformationUseCase;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
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

    public UpdatePersonalInformationController(
            UpdatePersonalInformationUseCase updatePersonalInformationUseCase,
            PersonalInformationUpdateWebMapper requestMapper,
            PersonalInformationUpdateResponseMapper responseMapper) {
        this.updatePersonalInformationUseCase = updatePersonalInformationUseCase;
        this.requestMapper = requestMapper;
        this.responseMapper = responseMapper;
    }

    @PutMapping
    public Mono<MutationResponse<PersonalInformationUpdateResultResponse>> update(
            @RequestBody Mono<UpdatePersonalInformationRequest> request) {
        return Mono.deferContextual(contextView -> {
            RequestHeaderContext headerContext = contextView.getOrDefault(RequestHeaderContextKeys.CONTEXT_KEY, null);
            String accountRef = headerContext == null ? null : headerContext.accountRef();
            if (accountRef == null || accountRef.isBlank()) {
                return Mono.error(new PortalAccessContextResolutionException(
                        ErrorCodes.MEMBER_CONTEXT_INVALID,
                        "Account-Ref is required for personal information update."));
            }
            return request
                    .map(body -> requestMapper.toCommand(accountRef, body.applyToAllAccounts(), body))
                    .flatMap(updatePersonalInformationUseCase::execute)
                    .map(responseMapper::toResponse);
        });
    }
}
