package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateResponseMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdatePersonalInformationRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.validation.PersonalInformationUpdateYamlValidator;
import com.bct.ngtpa.apiservice.application.port.in.UpdatePersonalInformationUseCase;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UpdatePersonalInformationYamlValidationRuntimeTest {

    @Test
    void yamlValidationFailureReturnsMutationErrorsAndSkipsMapperUseCaseAndResponseMapper() {
        UpdatePersonalInformationUseCase useCase = mock(UpdatePersonalInformationUseCase.class);
        PersonalInformationUpdateWebMapper requestMapper = mock(PersonalInformationUpdateWebMapper.class);
        PersonalInformationUpdateResponseMapper responseMapper = mock(PersonalInformationUpdateResponseMapper.class);
        PersonalInformationUpdateYamlValidator validator = mock(PersonalInformationUpdateYamlValidator.class);
        UpdatePersonalInformationController controller = new UpdatePersonalInformationController(
                useCase,
                requestMapper,
                responseMapper,
                validator);
        UpdatePersonalInformationRequest request = new UpdatePersonalInformationRequest(
                "1.0",
                false,
                Map.of("emailAddress", "bad email"));
        ApiError error = new ApiError(
                "FIELD",
                "personalInformation.email.invalid",
                "Please input a valid email address.",
                List.of("emailAddress"),
                "ERROR",
                "SERVER");
        when(validator.validate(eq(request), eq("zh-HK"), any(), any(), any()))
                .thenReturn(List.of(error));

        Mono<MutationResponse<PersonalInformationUpdateResultResponse>> response = controller.update(Mono.just(request))
                .contextWrite(context -> context.put(
                        RequestHeaderContextKeys.CONTEXT_KEY,
                        new RequestHeaderContext("ACC-001", "REQ-001", "zh-HK")));

        StepVerifier.create(response)
                .assertNext(body -> {
                    assertThat(body.success()).isFalse();
                    assertThat(body.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
                    assertThat(body.result()).isNull();
                    assertThat(body.messages()).isEmpty();
                    assertThat(body.errors()).containsExactly(error);
                })
                .verifyComplete();

        verify(validator).validate(eq(request), eq("zh-HK"), any(), any(), any());
        verifyNoInteractions(requestMapper, useCase, responseMapper);
    }

    @Test
    void missingAccountRefStillFailsBeforeYamlValidation() {
        UpdatePersonalInformationUseCase useCase = mock(UpdatePersonalInformationUseCase.class);
        PersonalInformationUpdateWebMapper requestMapper = mock(PersonalInformationUpdateWebMapper.class);
        PersonalInformationUpdateResponseMapper responseMapper = mock(PersonalInformationUpdateResponseMapper.class);
        PersonalInformationUpdateYamlValidator validator = mock(PersonalInformationUpdateYamlValidator.class);
        UpdatePersonalInformationController controller = new UpdatePersonalInformationController(
                useCase,
                requestMapper,
                responseMapper,
                validator);
        UpdatePersonalInformationRequest request = new UpdatePersonalInformationRequest(
                "1.0",
                false,
                Map.of("emailAddress", "member@example.test"));

        Mono<MutationResponse<PersonalInformationUpdateResultResponse>> response = controller.update(Mono.just(request))
                .contextWrite(context -> context.put(
                        RequestHeaderContextKeys.CONTEXT_KEY,
                        new RequestHeaderContext(null, "REQ-001", "en")));

        StepVerifier.create(response)
                .expectErrorMatches(error -> error.getMessage().contains(
                        "Account-Ref is required for personal information update."))
                .verify();

        verifyNoInteractions(validator, requestMapper, useCase, responseMapper);
    }
}
