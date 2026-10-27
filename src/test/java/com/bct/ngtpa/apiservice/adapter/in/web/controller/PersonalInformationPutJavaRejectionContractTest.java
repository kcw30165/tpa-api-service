package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.bct.ngtpa.apiservice.adapter.in.web.advice.ApiExceptionHandler;
import com.bct.ngtpa.apiservice.adapter.in.web.error.*;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.*;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PersonalInformationYamlContextRunner;
import com.bct.ngtpa.apiservice.adapter.in.web.personalinformation.PersonalInformationController;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.validation.*;
import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.port.in.*;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class PersonalInformationPutJavaRejectionContractTest {
  private final PersonalInformationUpdateYamlValidator yaml =
      mock(PersonalInformationUpdateYamlValidator.class);
  private final PersonalInformationFieldAccessChecker access =
      mock(PersonalInformationFieldAccessChecker.class);
  private final UpdatePersonalInformationUseCase update =
      mock(UpdatePersonalInformationUseCase.class);

  @Test
  void numericEmailIs400BeforeYamlOrAccessLookup() {
    structuralFailure("{\"emailAddress\":123}");
  }

  @Test
  void uiOnlySubmissionIs400WithoutMessage() {
    structuralFailure("{\"applyToAllMemberAccounts\":true}");
  }

  @Test
  void yamlAcceptedEmailReachesUseCaseWithoutAdditionalMapperContentRules() {
    when(yaml.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
    when(access.check(any(), any(), any(), any(), any())).thenReturn(Mono.just(List.of()));
    when(update.execute(any())).thenReturn(Mono.just(List.of()));

    put("{\"emailAddress\":\"member@.example.test\"}", 200, null);

    var command = org.mockito.ArgumentCaptor.forClass(UpdatePersonalInformationCommand.class);
    verify(update).execute(command.capture());
    assertThat(command.getValue().updateFields()).containsEntry("email", "member@.example.test");
  }

  @Test
  void yamlFailureIs422CodeOnly() {
    contentFailure(
        List.of(error("FIELD", "personalInformation.email.invalid", "emailAddress")),
        List.of(),
        "[{\"type\":\"FIELD\",\"code\":\"personalInformation.email.invalid\",\"targets\":[\"emailAddress\"]}]");
  }

  @Test
  void notEditableIs422CodeOnly() {
    contentFailure(
        List.of(),
        List.of(error("FIELD", "personalInformation.field.notEditable", "emailAddress")),
        "[{\"type\":\"FIELD\",\"code\":\"personalInformation.field.notEditable\",\"targets\":[\"emailAddress\"]}]");
  }

  @Test
  void crossFieldIs422WithBothTargets() {
    contentFailure(
        List.of(
            error(
                "CROSS_FIELD",
                "personalInformation.address.atLeastOneRequired",
                "residentialAddressLine1",
                "mailingAddressLine1")),
        List.of(),
        "[{\"type\":\"CROSS_FIELD\",\"code\":\"personalInformation.address.atLeastOneRequired\","
            + "\"targets\":[\"residentialAddressLine1\",\"mailingAddressLine1\"]}]");
  }

  @Test
  void yamlAndAccessDeduplicateAfterDroppingMessageMetadataAndPreserveOrder() {
    var first = error("FIELD", "personalInformation.field.required", "emailAddress");
    var duplicate =
        new ApiError(
            first.type(), first.code(), "different text", first.targets(), "WARN", "OTHER");
    contentFailure(
        List.of(first),
        List.of(duplicate, error("FIELD", "personalInformation.field.notEditable", "smsLanguage")),
        "[{\"type\":\"FIELD\",\"code\":\"personalInformation.field.required\",\"targets\":[\"emailAddress\"]},"
            + "{\"type\":\"FIELD\",\"code\":\"personalInformation.field.notEditable\",\"targets\":[\"smsLanguage\"]}]");
  }

  private void structuralFailure(String fields) {
    when(yaml.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
    when(access.check(any(), any(), any(), any(), any())).thenReturn(Mono.just(List.of()));
    put(fields, 400, null);
    verifyNoInteractions(yaml, access, update);
  }

  private void contentFailure(
      List<ApiError> yamlErrors, List<ApiError> accessErrors, String errors) {
    when(yaml.validate(any(), any(), any(), any(), any())).thenReturn(yamlErrors);
    when(access.check(any(), any(), any(), any(), any())).thenReturn(Mono.just(accessErrors));
    put("{\"emailAddress\":\"member@example.test\"}", 422, errors);
    verifyNoInteractions(update);
  }

  private void put(String fields, int status, String errors) {
    PersonalInformationYamlContextRunner.create()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var resolver =
                  new CurrentPortalAccessContextResolver() {
                    public Mono<PortalAccessContext> current() {
                      return Mono.just(
                          new PortalAccessContext(
                              new ActorContext("test", ActorUserRole.ROLE_USER_MBR),
                              new AccountContext(
                                  "ACC", "DB", "118", "2", "JPM", "OE", TermStatus.BLANK, null)));
                    }

                    public Mono<PortalAccessContext> currentOrEmpty() {
                      return current();
                    }
                  };
              var responseMapper = mock(PersonalInformationUpdateResponseMapper.class);
              when(responseMapper.toResponse(anyList()))
                  .thenReturn(
                      new com.bct.ngtpa.apiservice.adapter.in.web.response
                          .UpdatePersonalInformationResponse(
                          true,
                          com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus.UPDATED,
                          null,
                          List.of(),
                          List.of()));
              var controller =
                  new PersonalInformationController(
                      mock(GetPersonalInformationUseCase.class),
                      mock(PersonalInformationWebMapper.class),
                      resolver,
                      update,
                      new PersonalInformationUpdateWebMapper(
                          context.getBean(BffPagesProperties.class)),
                      responseMapper,
                      yaml,
                      exchange -> Optional.empty(),
                      access);
              var result =
                  WebTestClient.bindToController(controller)
                      .controllerAdvice(
                          new ApiExceptionHandler(
                              new ExceptionMappingRegistryConfig().exceptionMappingRegistry(),
                              new PublicMessageResolver(
                                  (code, locale, env, trust, scheme) -> "resolved message"),
                              new ErrorEventLogger(mock(LoggingSanitizer.class)),
                              new ErrorResponseRenderer()))
                      .build()
                      .put()
                      .uri("/api/v1/personal-information")
                      .contentType(MediaType.APPLICATION_JSON)
                      .bodyValue("{\"fields\":" + fields + "}")
                      .exchange()
                      .expectStatus()
                      .value(actual -> assertThat(actual).isEqualTo(status))
                      .expectBody();
              if (status == 200) {
                result
                    .jsonPath("$.success")
                    .isEqualTo(true)
                    .jsonPath("$.status")
                    .isEqualTo("UPDATED");
              } else if (errors == null) {
                result
                    .jsonPath("$.status")
                    .isEqualTo("VALIDATION_FAILED")
                    .jsonPath("$.errors[0].code")
                    .isEqualTo("err.personal-information.update.request.invalid")
                    .jsonPath("$.errors[0].message")
                    .doesNotExist();
              } else {
                result.consumeWith(
                    response -> {
                      try {
                        var json = new ObjectMapper();
                        assertThat(json.readTree(response.getResponseBody()))
                            .isEqualTo(
                                json.readTree(
                                    "{\"success\":false,\"status\":\"VALIDATION_FAILED\",\"result\":null,\"messages\":[],\"errors\":"
                                        + errors
                                        + "}"));
                      } catch (java.io.IOException exception) {
                        throw new AssertionError(exception);
                      }
                    });
              }
            });
  }

  private static ApiError error(String type, String code, String... targets) {
    return new ApiError(type, code, "must not leak", List.of(targets), "ERROR", "SERVER");
  }
}
