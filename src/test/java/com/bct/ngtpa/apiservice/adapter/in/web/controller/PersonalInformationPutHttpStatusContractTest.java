package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateResponseMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.personalinformation.PersonalInformationController;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdatePersonalInformationRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.UpdatePersonalInformationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateErrorMapper.MappedError;
import com.bct.ngtpa.apiservice.adapter.in.web.validation.PersonalInformationFieldAccessChecker;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

/**
 * ST-4: downstream selected-account rejection is 422 VALIDATION_FAILED.
 * Java pre-validation remains 400 until ST-5; success outcomes stay 200.
 *
 * <p>Statuses are compared as integers: isEqualTo(422) would resolve to UNPROCESSABLE_CONTENT while
 * the classification returns UNPROCESSABLE_ENTITY, two enum constants for the same code.
 */
class PersonalInformationPutHttpStatusContractTest {

  private static final UpdatePersonalInformationRequest REQUEST =
      new UpdatePersonalInformationRequest(
          "1.0", true, Map.of("emailAddress", "member@example.test"));
  private static final ApiError YAML_ERROR =
      ApiError.field("personalInformation.email.invalid", null, List.of("emailAddress"), "SERVER");
  private static final ApiError NOT_EDITABLE =
      ApiError.field(
          "personalInformation.field.notEditable", null, List.of("emailAddress"), "SERVER");
  private static final MappedError REJECTED =
      new MappedError("FIELD", "personalInformation.update.rejected", List.of("emailAddress"));

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

  // ----- failures decided by the BFF before the downstream is called -------------------------

  @Test
  void yamlValidationErrorsReturnHttp400WithTheErrorsArray() {
    when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of(YAML_ERROR));
    when(accessChecker.check(any(), any(), any(), any(), any())).thenReturn(Mono.just(List.of()));

    put()
        .expectStatus()
        .value(status -> assertThat(status).isEqualTo(400))
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false)
        .jsonPath("$.status")
        .isEqualTo("VALIDATION_FAILED")
        .jsonPath("$.result")
        .doesNotExist()
        .jsonPath("$.messages")
        .isArray()
        .jsonPath("$.errors[0].code")
        .isEqualTo("personalInformation.email.invalid")
        .jsonPath("$.errors[0].targets[0]")
        .isEqualTo("emailAddress");
  }

  @Test
  void accessErrorsReturnHttp400WithTheErrorsArray() {
    when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
    when(accessChecker.check(any(), any(), any(), any(), any()))
        .thenReturn(Mono.just(List.of(NOT_EDITABLE)));

    put()
        .expectStatus()
        .value(status -> assertThat(status).isEqualTo(400))
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false)
        .jsonPath("$.status")
        .isEqualTo("VALIDATION_FAILED")
        .jsonPath("$.errors[0].code")
        .isEqualTo("personalInformation.field.notEditable");
  }

  // ----- failures reported by the downstream (always HTTP 200 there) -------------------------

  @Test
  void downstreamValidationRejectionReturnsHttp422() {
    inputsAreValid();
    downstreamReturns(
        new UpdatePersonalInformationResponse<>(
            false, ApiStatus.VALIDATION_FAILED, failedResult(), List.of(), List.of(REJECTED)));

    put()
        .expectStatus()
        .value(status -> assertThat(status).isEqualTo(422))
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false)
        .jsonPath("$.status")
        .isEqualTo("VALIDATION_FAILED")
        .jsonPath("$.errors[0].code")
        .isEqualTo("personalInformation.update.rejected");
  }

  @Test
  void selectedFailureKeepsTheDataObjectAndNeverUsesBusinessRejected() {
    inputsAreValid();
    downstreamReturns(
        new UpdatePersonalInformationResponse<>(
            false, ApiStatus.VALIDATION_FAILED, failedResult(), List.of(), List.of(REJECTED)));

    put()
        .expectStatus()
        .value(status -> assertThat(status).isEqualTo(422))
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false)
        .jsonPath("$.status")
        .isEqualTo("VALIDATION_FAILED")
        .jsonPath("$.result.data.rows")
        .isArray()
        .jsonPath("$.errors[0].code")
        .isEqualTo("personalInformation.update.rejected");
  }

  @Test
  void downstreamTechnicalFailureReturnsHttp503() {
    inputsAreValid();
    downstreamReturns(
        new UpdatePersonalInformationResponse<>(
            false, ApiStatus.SYSTEM_ERROR, null, List.of(),
            List.of(new MappedError("SYSTEM", "err.personal-information.update.unavailable", List.of()))));

    put()
        .expectStatus()
        .value(status -> assertThat(status).isEqualTo(503))
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false)
        .jsonPath("$.status")
        .isEqualTo("SYSTEM_ERROR")
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.personal-information.update.unavailable");
  }

  @Test
  void javaPreValidationBodyRemainsIdenticalToTheExistingMutationContract() {
    when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of(YAML_ERROR));
    when(accessChecker.check(any(), any(), any(), any(), any())).thenReturn(Mono.just(List.of()));
    put().expectStatus().isBadRequest().expectBody().consumeWith(response -> {
      var json = new com.fasterxml.jackson.databind.ObjectMapper();
      try {
        com.fasterxml.jackson.databind.JsonNode expected =
            json.valueToTree(MutationResponse.failure(ApiStatus.VALIDATION_FAILED, List.of(YAML_ERROR)));
        assertThat(json.readTree(response.getResponseBody())).isEqualTo(expected);
      } catch (java.io.IOException error) {
        throw new AssertionError(error);
      }
    });
  }

  // ----- outcomes that stay HTTP 200 ----------------------------------------------------------

  @Test
  void partialSuccessStaysHttp200() {
    inputsAreValid();
    downstreamReturns(
        new UpdatePersonalInformationResponse<>(
            true, ApiStatus.PARTIAL_SUCCESS, okResult(),
            List.of(new UpdatePersonalInformationResponse.Message("personalInformation.update.partialSuccess")),
            List.of()));

    put()
        .expectStatus()
        .value(status -> assertThat(status).isEqualTo(200))
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("PARTIAL_SUCCESS");
  }

  @Test
  void successfulUpdateReturnsHttp200() {
    inputsAreValid();
    downstreamReturns(new UpdatePersonalInformationResponse<>(
        true, ApiStatus.UPDATED, okResult(),
        List.of(new UpdatePersonalInformationResponse.Message("personalInformation.update.success")),
        List.of()));

    put()
        .expectStatus()
        .value(status -> assertThat(status).isEqualTo(200))
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(true)
        .jsonPath("$.status")
        .isEqualTo("UPDATED");
  }

  // ----- helpers ------------------------------------------------------------------------------

  private void inputsAreValid() {
    when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
    when(accessChecker.check(any(), any(), any(), any(), any())).thenReturn(Mono.just(List.of()));
  }

  private void downstreamReturns(
      UpdatePersonalInformationResponse<MappedError> mapped) {
    List<UpdatePersonalInformationResult> results = List.of();
    when(requestMapper.toCommand(eq(true), any(UpdatePersonalInformationRequest.class)))
        .thenReturn(new UpdatePersonalInformationCommand(true, Map.of("email", "a@b.test")));
    when(useCase.execute(any(UpdatePersonalInformationCommand.class)))
        .thenReturn(Mono.just(results));
    when(responseMapper.toResponse(results)).thenReturn(mapped);
  }

  private static PersonalInformationUpdateResultResponse failedResult() {
    return new PersonalInformationUpdateResultResponse(
        new PersonalInformationUpdateResultResponse.Data(null, List.of(), List.of()));
  }

  private static PersonalInformationUpdateResultResponse okResult() {
    return new PersonalInformationUpdateResultResponse(
        new PersonalInformationUpdateResultResponse.Data(
            new PersonalInformationUpdateResultResponse.Account(
                "00000000118-0002", "REF-1", "2026-10-23", "13:00:00"), List.of(), List.of()));
  }

  private WebTestClient.ResponseSpec put() {
    return client()
        .put()
        .uri("/api/v1/personal-information")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(REQUEST)
        .exchange();
  }

  private WebTestClient client() {
    var controller =
        new PersonalInformationController(
            mock(GetPersonalInformationUseCase.class),
            mock(PersonalInformationWebMapper.class),
            resolver(context("ACC-123")),
            useCase,
            requestMapper,
            responseMapper,
            validator,
            exchange -> Optional.empty(),
            accessChecker);
    WebFilter requestHeaderContext =
        (serverExchange, chain) ->
            chain
                .filter(serverExchange)
                .contextWrite(
                    reactorContext ->
                        reactorContext.put(
                            RequestHeaderContextKeys.CONTEXT_KEY,
                            new RequestHeaderContext("ACC-123", "req-9", "en")));
    return WebTestClient.bindToController(controller).webFilter(requestHeaderContext).build();
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
