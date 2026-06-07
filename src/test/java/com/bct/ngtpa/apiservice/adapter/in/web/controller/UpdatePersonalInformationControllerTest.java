package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateResponseMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdatePersonalInformationRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateResultResponse;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.in.UpdatePersonalInformationUseCase;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class UpdatePersonalInformationControllerTest {

    @Test
    void returnsMutationResponseForSuccessfulUpdate() {
        PersonalInformationUpdateWebMapper requestMapper = mock(PersonalInformationUpdateWebMapper.class);
        PersonalInformationUpdateResponseMapper responseMapper = mock(PersonalInformationUpdateResponseMapper.class);
        UpdatePersonalInformationUseCase useCase = mock(UpdatePersonalInformationUseCase.class);
        UpdatePersonalInformationRequest request = new UpdatePersonalInformationRequest("1.0", true, Map.of("emailAddress", "a@b.com"));
        UpdatePersonalInformationCommand command = new UpdatePersonalInformationCommand("ACC-2", true, Map.of("email", "a@b.com"));
        UpdatePersonalInformationResult result = new UpdatePersonalInformationResult(true, "990000001", "2026-06-07", "08:36:51");
        MutationResponse<PersonalInformationUpdateResultResponse> mapped = MutationResponse.success(
                ApiStatus.UPDATED,
                new PersonalInformationUpdateResultResponse("990000001", "2026-06-07", "08:36:51"),
                java.util.List.of());
        when(requestMapper.toCommand("ACC-2", true, request)).thenReturn(command);
        when(useCase.execute(command)).thenReturn(Mono.just(result));
        when(responseMapper.toResponse(result)).thenReturn(mapped);

        MutationResponse<PersonalInformationUpdateResultResponse> response = new UpdatePersonalInformationController(
                useCase, requestMapper, responseMapper)
                .update(Mono.just(request))
                .contextWrite(ctx -> ctx.put(RequestHeaderContextKeys.CONTEXT_KEY,
                        new RequestHeaderContext("ACC-2", "client-request-uuid", "en")))
                .block();

        assertThat(response).isSameAs(mapped);
    }

    @Test
    void rejectsMissingAccountRef() {
        UpdatePersonalInformationUseCase useCase = command -> Mono.empty();
        PersonalInformationUpdateWebMapper requestMapper = mock(PersonalInformationUpdateWebMapper.class);
        PersonalInformationUpdateResponseMapper responseMapper = mock(PersonalInformationUpdateResponseMapper.class);

        assertThrows(PortalAccessContextResolutionException.class, () -> new UpdatePersonalInformationController(
                useCase, requestMapper, responseMapper)
                .update(Mono.just(new UpdatePersonalInformationRequest("1.0", true, Map.of())))
                .contextWrite(ctx -> ctx.put(RequestHeaderContextKeys.CONTEXT_KEY,
                        new RequestHeaderContext(" ", "client-request-uuid", "en")))
                .block());
    }
}
