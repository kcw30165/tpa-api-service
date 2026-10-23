package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieReader;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateResponseMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.personalinformation.PersonalInformationController;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdatePersonalInformationRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.validation.PersonalInformationFieldAccessChecker;
import com.bct.ngtpa.apiservice.adapter.in.web.validation.PersonalInformationUpdateYamlValidator;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
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

/** Sub-task 3.4c: the PUT controller combines the YAML and the access errors before mapping. */
class PersonalInformationControllerAccessCheckTest {

  private static final AuthenticatedSessionId SID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final UpdatePersonalInformationRequest REQUEST =
      new UpdatePersonalInformationRequest(
          "1.0", true, Map.of("emailAddress", "member@example.test"));
  private static final ApiError NOT_EDITABLE =
      ApiError.field(
          "personalInformation.field.notEditable", null, List.of("emailAddress"), "SERVER");
  private static final ApiError YAML_ERROR =
      ApiError.field(
          "personalInformation.email.invalid",
          "Please input a valid email address.",
          List.of("emailAddress"),
          "SERVER");

  private final UpdatePersonalInformationUseCase useCase =
      mock(UpdatePersonalInformationUseCase.class);
  private final PersonalInformationUpdateWebMapper requestMapper =
      mock(PersonalInformationUpdateWebMapper.class);
  private final PersonalInformationUpdateResponseMapper responseMapper =
      mock(PersonalInformationUpdateResponseMapper.class);
  private final PersonalInformationUpdateYamlValidator validator =
      mock(PersonalInformationUpdateYamlValidator.class);
  private final PersonalInformationFieldAccessChecker accessChecker =
      mock(PersonalInformationFieldAccessChecker.class);

  private PersonalInformationController controller(
      String accountRef, AuthenticatedSessionCookieReader reader) {
    return new PersonalInformationController(
        mock(GetPersonalInformationUseCase.class),
        mock(PersonalInformationWebMapper.class),
        resolver(context(accountRef)),
        useCase,
        requestMapper,
        responseMapper,
        validator,
        reader,
        accessChecker);
  }

  private static Mono<MutationResponse<List<PersonalInformationUpdateResultResponse>>> put(
      PersonalInformationController controller, String accountRefInHeader) {
    return controller
        .update(Mono.just(REQUEST), "en", exchange())
        .contextWrite(
            ctx ->
                ctx.put(
                    RequestHeaderContextKeys.CONTEXT_KEY,
                    new RequestHeaderContext(accountRefInHeader, "req-7", "en")));
  }

  @Test
  void accessErrorsAreReturnedAsValidationFailedAndNothingIsMappedOrExecuted() {
    when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
    when(accessChecker.check(any(), any(), any(), any(), any()))
        .thenReturn(Mono.just(List.of(NOT_EDITABLE)));

    StepVerifier.create(put(controller("ACC-123", exchange -> Optional.of(SID)), "ACC-123"))
        .assertNext(
            body -> {
              assertThat(body.success()).isFalse();
              assertThat(body.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
              assertThat(body.result()).isNull();
              assertThat(body.errors()).containsExactly(NOT_EDITABLE);
            })
        .verifyComplete();

    verifyNoInteractions(requestMapper, useCase, responseMapper);
  }

  @Test
  void yamlAndAccessErrorsAreCombinedInOneResponseWithoutDuplicates() {
    when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of(YAML_ERROR));
    when(accessChecker.check(any(), any(), any(), any(), any()))
        .thenReturn(Mono.just(List.of(NOT_EDITABLE, YAML_ERROR)));

    StepVerifier.create(put(controller("ACC-123", exchange -> Optional.of(SID)), "ACC-123"))
        .assertNext(
            body -> {
              assertThat(body.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
              assertThat(body.errors()).containsExactly(YAML_ERROR, NOT_EDITABLE);
            })
        .verifyComplete();

    verifyNoInteractions(requestMapper, useCase, responseMapper);
  }

  @Test
  void accessCheckerReceivesRequestTrustedCommandSessionAccountRefAndRequestId() {
    when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
    when(accessChecker.check(any(), any(), any(), any(), any()))
        .thenReturn(Mono.just(List.of(NOT_EDITABLE)));

    put(controller("ACC-123", exchange -> Optional.of(SID)), "ACC-123").block();

    verify(accessChecker)
        .check(
            REQUEST,
            new GetPersonalInformationCommand(
                "en", "JP", "policy-1", "cert-1", "user-1", ActorUserRole.ROLE_USER_MBR),
            Optional.of(SID),
            "ACC-123",
            "req-7");
    verify(validator).validate(REQUEST, "en", "JP", "JPM", "OE");
  }

  @Test
  void noErrorsLetsTheUpdateProceed() {
    List<UpdatePersonalInformationResult> results = List.of();
    MutationResponse<List<PersonalInformationUpdateResultResponse>> mapped =
        MutationResponse.success(ApiStatus.UPDATED, List.of(), List.of());
    when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
    when(accessChecker.check(any(), any(), any(), any(), any())).thenReturn(Mono.just(List.of()));
    when(requestMapper.toCommand(eq(true), any(UpdatePersonalInformationRequest.class)))
        .thenReturn(new UpdatePersonalInformationCommand(true, Map.of("email", "a@b.test")));
    when(useCase.execute(any(UpdatePersonalInformationCommand.class)))
        .thenReturn(Mono.just(results));
    when(responseMapper.toResponse(results)).thenReturn(mapped);

    StepVerifier.create(put(controller("ACC-123", exchange -> Optional.of(SID)), "ACC-123"))
        .assertNext(
            body -> {
              assertThat(body.success()).isTrue();
              assertThat(body.status()).isEqualTo(ApiStatus.UPDATED);
            })
        .verifyComplete();

    verify(useCase).execute(any(UpdatePersonalInformationCommand.class));
  }

  @Test
  void yamlErrorsAloneStillRejectTheUpdate() {
    when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of(YAML_ERROR));
    when(accessChecker.check(any(), any(), any(), any(), any())).thenReturn(Mono.just(List.of()));

    StepVerifier.create(put(controller("ACC-123", exchange -> Optional.of(SID)), "ACC-123"))
        .assertNext(
            body -> {
              assertThat(body.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
              assertThat(body.errors()).containsExactly(YAML_ERROR);
            })
        .verifyComplete();

    verifyNoInteractions(requestMapper, useCase, responseMapper);
  }

  @Test
  void aMissingAccountRefIsRejectedBeforeTheAccessCheck() {
    var controller = controller(" ", exchange -> Optional.of(SID));

    var pending = put(controller, " ");
    assertThrows(PortalAccessContextResolutionException.class, pending::block);

    verifyNoInteractions(accessChecker, requestMapper, useCase, responseMapper);
  }

  @Test
  void anAccessCheckFailureIsPropagatedAndNothingIsExecuted() {
    var failure = new IllegalStateException("downstream unavailable");
    when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
    when(accessChecker.check(any(), any(), any(), any(), any())).thenReturn(Mono.error(failure));

    StepVerifier.create(put(controller("ACC-123", exchange -> Optional.of(SID)), "ACC-123"))
        .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
        .verify();

    verifyNoInteractions(requestMapper, useCase, responseMapper);
  }

  @Test
  void aMissingSessionCookiePassesAnEmptySessionToTheAccessCheck() {
    when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
    when(accessChecker.check(any(), any(), any(), any(), any()))
        .thenReturn(Mono.just(List.of(NOT_EDITABLE)));

    put(controller("ACC-123", exchange -> Optional.empty()), "ACC-123").block();

    verify(accessChecker).check(any(), any(), eq(Optional.empty()), eq("ACC-123"), eq("req-7"));
  }

  private static MockServerWebExchange exchange() {
    return MockServerWebExchange.from(
        MockServerHttpRequest.put("/api/v1/personal-information").build());
  }

  private static CurrentPortalAccessContextResolver resolver(PortalAccessContext context) {
    return new CurrentPortalAccessContextResolver() {
      @Override
      public Mono<PortalAccessContext> current() {
        return Mono.just(context);
      }

      @Override
      public Mono<PortalAccessContext> currentOrEmpty() {
        return Mono.just(context);
      }
    };
  }

  private static PortalAccessContext context(String accountRef) {
    return new PortalAccessContext(
        new ActorContext("user-1", ActorUserRole.ROLE_USER_MBR),
        new AccountContext(
            accountRef, "JP", "policy-1", "cert-1", "JPM", "OE", TermStatus.BLANK, null));
  }
}
