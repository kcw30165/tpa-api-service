package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieReader;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateResponseMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.personalinformation.PersonalInformationController;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdatePersonalInformationRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.UpdatePersonalInformationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.validation.PersonalInformationUpdateYamlValidator;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import com.bct.ngtpa.apiservice.application.port.in.GetPersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.in.UpdatePersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UpdatePersonalInformationControllerTest {
  private static final AuthenticatedSessionCookieReader NO_SESSION = exchange -> Optional.empty();

  @Test
  void mapsRequestToCommandAndMapsResultListToMutationResponse() {
    UpdatePersonalInformationUseCase useCase = mock(UpdatePersonalInformationUseCase.class);
    PersonalInformationUpdateWebMapper requestMapper =
        mock(PersonalInformationUpdateWebMapper.class);
    PersonalInformationUpdateYamlValidator validator =
        mock(PersonalInformationUpdateYamlValidator.class);
    PersonalInformationUpdateResponseMapper responseMapper =
        mock(PersonalInformationUpdateResponseMapper.class);
    var controller =
        pIController(
            useCase,
            requestMapper,
            responseMapper,
            validator,
            currentPortalAccessContextResolver());

    UpdatePersonalInformationRequest request =
        new UpdatePersonalInformationRequest(
            "1.0", true, Map.of("emailAddress", "member@example.com"));
    UpdatePersonalInformationCommand command =
        new UpdatePersonalInformationCommand(true, Map.of("email", "member@example.com"));
    List<UpdatePersonalInformationResult> results =
        List.of(
            new UpdatePersonalInformationResult(
                true,
                true,
                "00000000118",
                "2",
                "DB",
                "260001373",
                "2025-12-31",
                "15:42:52",
                List.of()));
    UpdatePersonalInformationResponse mappedResponse =
        new UpdatePersonalInformationResponse(
            true,
            ApiStatus.UPDATED,
            new PersonalInformationUpdateResultResponse(
                new PersonalInformationUpdateResultResponse.Data(
                    new PersonalInformationUpdateResultResponse.Account(
                        "00000000118-0002", "260001373", "2025-12-31", "15:42:52"),
                    List.of(),
                    List.of())),
            List.of(
                new UpdatePersonalInformationResponse.Message(
                    "personalInformation.update.success")),
            List.of());

    when(requestMapper.toCommand(eq(true), any(UpdatePersonalInformationRequest.class)))
        .thenReturn(command);
    when(useCase.execute(any(UpdatePersonalInformationCommand.class)))
        .thenReturn(Mono.just(results));
    when(responseMapper.toResponse(results)).thenReturn(mappedResponse);

    StepVerifier.create(
            controller
                .update(Mono.just(request), null, exchange())
                .contextWrite(
                    context ->
                        context.put(
                            RequestHeaderContextKeys.CONTEXT_KEY,
                            new RequestHeaderContext("ACC-123", "RID-001", "en"))))
        .assertNext(
            response -> {
              assertThat(response.success()).isTrue();
              assertThat(response.status()).isEqualTo(ApiStatus.UPDATED);
              assertThat(response.result().data().rows()).isEmpty();
              assertThat(response.result().data().selectedAccount().refNo()).isEqualTo("260001373");
            })
        .verifyComplete();
  }

  private static MockServerWebExchange exchange() {
    return MockServerWebExchange.from(
        MockServerHttpRequest.put("/api/v1/personal-information").build());
  }

  private static CurrentPortalAccessContextResolver currentPortalAccessContextResolver() {
    return new CurrentPortalAccessContextResolver() {
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
        new ActorContext("userId_for_update", ActorUserRole.ROLE_USER_MBR),
        new AccountContext(
            "ACC-123",
            "accountEnv_for_update",
            "policyNo_for_update",
            "certNo_for_update",
            "trustCode_for_update",
            "schemeType_for_update",
            TermStatus.BLANK,
            null));
  }

  private static PersonalInformationController pIController(
      UpdatePersonalInformationUseCase useCase,
      PersonalInformationUpdateWebMapper requestMapper,
      PersonalInformationUpdateResponseMapper responseMapper,
      PersonalInformationUpdateYamlValidator validator,
      CurrentPortalAccessContextResolver resolver) {

    GetPersonalInformationUseCase getUseCase = mock(GetPersonalInformationUseCase.class);
    PersonalInformationWebMapper getRequestMapper = mock(PersonalInformationWebMapper.class);

    return new PersonalInformationController(
        getUseCase,
        getRequestMapper,
        resolver,
        useCase,
        requestMapper,
        responseMapper,
        validator,
        NO_SESSION,
        PersonalInformationAccessCheckerTestSupport.passing());
  }
}
