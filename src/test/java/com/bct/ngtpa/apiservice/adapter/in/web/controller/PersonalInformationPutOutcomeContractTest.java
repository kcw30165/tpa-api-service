package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PageDisplayTextResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateErrorMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateResponseMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PersonalInformationYamlContextRunner;
import com.bct.ngtpa.apiservice.adapter.in.web.personalinformation.PersonalInformationController;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdatePersonalInformationRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.validation.PersonalInformationUpdateYamlValidator;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimUpdatePersonalInformationAdapter;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateMemberInfoApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationError;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import com.bct.ngtpa.apiservice.application.port.in.GetPersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.application.service.account.AccountIdentifierFormatter;
import com.bct.ngtpa.apiservice.application.usecase.UpdatePersonalInformationService;
import com.bct.ngtpa.apiservice.shared.config.ConfigVariantCandidateGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class PersonalInformationPutOutcomeContractTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String SELECTED =
      """
      {"accountIdentifier":"00000000118-0002","refNo":null,"submitDate":"","submitTime":" "}
      """;
  private static final String OTHER =
      """
      {"accountIdentifier":"00000000118-0003","refNo":"OA-REF","submitDate":"raw date","submitTime":null}
      """;
  private static final String SELECTED_ERROR =
      """
      {"type":"CROSS_FIELD","code":"personalInformation.address.atLeastOneRequired",
       "targets":["residentialAddressLine1","residentialAddressLine2"]}
      """;
  private static final String OTHER_ERROR =
      """
      {"accountIdentifier":"00000000118-0003","errors":[
        {"type":"FIELD","code":"personalInformation.email.invalid","targets":["emailAddress"]}]}
      """;

  @Test
  void ssReturns200UpdatedAndOnlySuccessfulOtherRows() {
    assertOutcome(
        true,
        true,
        200,
        "UPDATED",
        SELECTED,
        OTHER,
        "",
        "",
        "{\"code\":\"personalInformation.update.success\"}");
  }

  @Test
  void sfReturns200PartialSuccessWithOtherErrorsOnlyInData() {
    assertOutcome(
        true,
        false,
        200,
        "PARTIAL_SUCCESS",
        SELECTED,
        "",
        OTHER_ERROR,
        "",
        "{\"code\":\"personalInformation.update.partialSuccess\"}");
  }

  @Test
  void fsReturns422ValidationFailedWithSelectedErrorsOnlyAtRoot() {
    assertOutcome(false, true, 422, "VALIDATION_FAILED", "null", OTHER, "", SELECTED_ERROR, "");
  }

  @Test
  void ffReturns422ValidationFailedAndKeepsErrorAccountsSeparate() {
    assertOutcome(
        false, false, 422, "VALIDATION_FAILED", "null", "", OTHER_ERROR, SELECTED_ERROR, "");
  }

  @Test
  void tasMissingOverseasCountryReturns422WithYamlTargetAndVerbatimOtherAccountMetadata() {
    PersonalInformationYamlContextRunner.create()
        .run(
            context -> {
              var properties = context.getBean(BffPagesProperties.class);
              String downstreamJson =
                  """
              {"response":{"err-message":"","data":[
                {"success":false,"policy-no":"118","cert-no":"2","env":"JP",
                 "ref-no":null,"submit-date":null,"submit-time":null,
                 "errors":[{"type":"CROSS_FIELD","fields":"other-phone-country|other-phone",
                            "code":"MISSING_OVERSEAS_COUNTRY"}]},
                {"success":true,"policy-no":"118","cert-no":"3","env":"JP",
                 "ref-no":"","submit-date":null,"submit-time":""}]}}
              """;
              ApimResponseEnvelope<UpdateMemberInfoApimDataItem> downstream =
                  JSON.readValue(downstreamJson, new TypeReference<>() {});
              var executor = mock(ApimExchangeExecutor.class);
              when(executor.postEnvelope(any())).thenAnswer(ignored -> Mono.just(downstream));
              var formatter = new AccountIdentifierFormatter(11, 4, '0');
              var contextResolver = mock(CurrentPortalAccessContextResolver.class);
              when(contextResolver.current())
                  .thenReturn(
                      Mono.just(
                          new PortalAccessContext(
                              new ActorContext("test-user", ActorUserRole.ROLE_USER_MBR),
                              new AccountContext(
                                  "ACC-123",
                                  "JP",
                                  "118",
                                  "2",
                                  "JPM",
                                  "OE",
                                  TermStatus.BLANK,
                                  null))));
              var generator = new ConfigVariantCandidateGenerator();
              var controller =
                  new PersonalInformationController(
                      mock(GetPersonalInformationUseCase.class),
                      mock(PersonalInformationWebMapper.class),
                      contextResolver,
                      new UpdatePersonalInformationService(
                          new ApimUpdatePersonalInformationAdapter(executor, formatter)),
                      new PersonalInformationUpdateWebMapper(properties),
                      new PersonalInformationUpdateResponseMapper(
                          new PersonalInformationUpdateErrorMapper(properties), formatter),
                      new PersonalInformationUpdateYamlValidator(
                          properties, new PageDisplayTextResolver(generator), generator),
                      exchange -> Optional.empty(),
                      PersonalInformationAccessCheckerTestSupport.passing());
              String expected =
                  """
              {"success":false,"status":"VALIDATION_FAILED",
               "result":{"data":{"selectedAccount":null,
                 "rows":[{"accountIdentifier":"00000000118-0003","refNo":"",
                          "submitDate":null,"submitTime":""}],"errors":[]}},
               "messages":[],"errors":[{"type":"CROSS_FIELD",
                 "code":"personalInformation.overseasCountryCode.requiredWhenPhonePresent",
                 "targets":["overseasCountryCode"]}]}
              """;
              WebTestClient.bindToController(controller)
                  .build()
                  .put()
                  .uri("/api/v1/personal-information")
                  .contentType(MediaType.APPLICATION_JSON)
                  .bodyValue(
                      new UpdatePersonalInformationRequest(
                          "1.0",
                          true,
                          Map.of(
                              "residentialAddressLine1",
                              "1 Example Street",
                              "overseasPhoneNumber",
                              "12345678")))
                  .exchange()
                  .expectStatus()
                  .isEqualTo(422)
                  .expectBody()
                  .consumeWith(
                      response -> {
                        try {
                          assertThat(JSON.readTree(response.getResponseBody()))
                              .isEqualTo(JSON.readTree(expected));
                        } catch (java.io.IOException error) {
                          throw new AssertionError(error);
                        }
                      });
            });
  }

  private void assertOutcome(
      boolean selectedSuccess,
      boolean otherSuccess,
      int http,
      String status,
      String selected,
      String rows,
      String otherErrors,
      String rootErrors,
      String messages) {
    PersonalInformationYamlContextRunner.create()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var mapper =
                  new PersonalInformationUpdateResponseMapper(
                      new PersonalInformationUpdateErrorMapper(
                          context.getBean(BffPagesProperties.class)),
                      new com.bct.ngtpa.apiservice.application.service.account
                          .AccountIdentifierFormatter(11, 4, '0'));
              var validator = mock(PersonalInformationUpdateYamlValidator.class);
              when(validator.validate(any(), any(), any(), any(), any())).thenReturn(List.of());
              var controller =
                  new PersonalInformationController(
                      mock(GetPersonalInformationUseCase.class),
                      mock(PersonalInformationWebMapper.class),
                      resolver(),
                      command ->
                          Mono.just(List.of(row(false, otherSuccess), row(true, selectedSuccess))),
                      new PersonalInformationUpdateWebMapper(
                          context.getBean(BffPagesProperties.class)),
                      mapper,
                      validator,
                      exchange -> Optional.empty(),
                      PersonalInformationAccessCheckerTestSupport.passing());
              String expected =
                  """
          {"success":%s,"status":"%s",
           "result":{"data":{"selectedAccount":%s,"rows":[%s],"errors":[%s]}},
           "messages":[%s],"errors":[%s]}
          """
                      .formatted(
                          selectedSuccess,
                          status,
                          selected,
                          rows,
                          otherErrors,
                          messages,
                          rootErrors);
              WebTestClient.bindToController(controller)
                  .build()
                  .put()
                  .uri("/api/v1/personal-information")
                  .contentType(MediaType.APPLICATION_JSON)
                  .bodyValue(
                      new UpdatePersonalInformationRequest(
                          "1.0", true, Map.of("emailAddress", "member@example.test")))
                  .exchange()
                  .expectStatus()
                  .value(actual -> assertThat(actual).isEqualTo(http))
                  .expectBody()
                  .consumeWith(
                      response -> {
                        try {
                          assertThat(JSON.readTree(response.getResponseBody()))
                              .isEqualTo(JSON.readTree(expected));
                        } catch (java.io.IOException error) {
                          throw new AssertionError(error);
                        }
                      });
            });
  }

  private static UpdatePersonalInformationResult row(boolean selected, boolean success) {
    return new UpdatePersonalInformationResult(
        selected,
        success,
        "00118",
        selected ? "02" : "0003",
        "DB",
        selected ? null : "OA-REF",
        selected ? "" : "raw date",
        selected ? " " : null,
        success
            ? List.of()
            : selected
                ? List.of(
                    new UpdatePersonalInformationError(
                        "CROSS_FIELD", List.of("addr1|addr2"), "AT_LEAST_ONE_REQUIRED"))
                : List.of(
                    new UpdatePersonalInformationError(
                        "FIELD", List.of("email"), "INVALID_FORMAT")));
  }

  private static CurrentPortalAccessContextResolver resolver() {
    return new CurrentPortalAccessContextResolver() {
      public Mono<PortalAccessContext> current() {
        return Mono.just(
            new PortalAccessContext(
                new ActorContext("test-user", ActorUserRole.ROLE_USER_MBR),
                new AccountContext(
                    "ACC-123", "DB", "118", "2", "JPM", "OE", TermStatus.BLANK, null)));
      }

      public Mono<PortalAccessContext> currentOrEmpty() {
        return current();
      }
    };
  }
}
