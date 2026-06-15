package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.bct.ngtpa.apiservice.application.port.in.UpdatePersonalInformationUseCase;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UpdatePersonalInformationControllerTest {

    @Test
    void mapsRequestToCommandAndMapsResultListToMutationResponse() {
        UpdatePersonalInformationUseCase useCase = mock(UpdatePersonalInformationUseCase.class);
        PersonalInformationUpdateWebMapper requestMapper = mock(PersonalInformationUpdateWebMapper.class);
        PersonalInformationUpdateResponseMapper responseMapper = mock(PersonalInformationUpdateResponseMapper.class);
        UpdatePersonalInformationController controller = new UpdatePersonalInformationController(
                useCase, requestMapper, responseMapper);

        UpdatePersonalInformationRequest request = new UpdatePersonalInformationRequest(
                "1.0",
                true,
                Map.of("emailAddress", "member@example.com"));
        UpdatePersonalInformationCommand command = new UpdatePersonalInformationCommand(
                "ACC-123",
                true,
                Map.of("email", "member@example.com"));
        List<UpdatePersonalInformationResult> results = List.of(new UpdatePersonalInformationResult(
                true,
                true,
                "00000000118",
                "2",
                "DB",
                "260001373",
                "2025-12-31",
                "15:42:52",
                List.of()));
        MutationResponse<List<PersonalInformationUpdateResultResponse>> mappedResponse = MutationResponse.success(
                ApiStatus.UPDATED,
                List.of(new PersonalInformationUpdateResultResponse(
                        true,
                        true,
                        "00000000118",
                        "2",
                        "DB",
                        "260001373",
                        "2025-12-31",
                        "15:42:52",
                        List.of())),
                List.of());

        when(requestMapper.toCommand(eq("ACC-123"), eq(true), any(UpdatePersonalInformationRequest.class)))
                .thenReturn(command);
        when(useCase.execute(command)).thenReturn(Mono.just(results));
        when(responseMapper.toResponse(results)).thenReturn(mappedResponse);

        StepVerifier.create(controller.update(Mono.just(request))
                        .contextWrite(context -> context.put(
                                RequestHeaderContextKeys.CONTEXT_KEY,
                                new RequestHeaderContext("ACC-123", "RID-001", "en"))))
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.status()).isEqualTo(ApiStatus.UPDATED);
                    assertThat(response.result()).hasSize(1);
                    assertThat(response.result().getFirst().refNo()).isEqualTo("260001373");
                })
                .verifyComplete();
    }
}
