package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextProvider;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
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
                validator,
                currentPortalAccessContextProvider()
        );
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
        when(validator.validate(eq(request), eq("en"), any(), any(), any())).thenReturn(List.of(error));

        Mono<MutationResponse<List<PersonalInformationUpdateResultResponse>>> response = controller.update(Mono.just(request), null)
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

        verify(validator).validate(eq(request), eq("en"), any(), any(), any());
        verifyNoInteractions(requestMapper, useCase, responseMapper);
    }

    private static CurrentPortalAccessContextProvider currentPortalAccessContextProvider() {
        return new CurrentPortalAccessContextProvider() {
            @Override
            public Mono<PortalAccessContext> current() {
                return Mono.just(portalAccessContext());
            }

            @Override
            public Mono<PortalAccessContext> currentOrEmpty() {
                return Mono.just(portalAccessContext());
            }
        };
    }

    private static PortalAccessContext portalAccessContext() {
        return new PortalAccessContext(
                new ActorContext("userId_for_update", "MEMBER"),
                new AccountContext("ACC-123", "accountEnv_for_update", "policyNo_for_update", "certNo_for_update",
                        "trustCode_for_update", "schemeType_for_update", TermStatus.BLANK, null));
    }

}
