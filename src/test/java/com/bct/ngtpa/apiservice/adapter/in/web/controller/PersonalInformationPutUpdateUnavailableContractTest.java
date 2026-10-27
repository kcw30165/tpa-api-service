package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.advice.ApiExceptionHandler;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorEventLogger;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorResponseRenderer;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistryConfig;
import com.bct.ngtpa.apiservice.adapter.in.web.error.PublicMessageResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateResponseMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationUpdateWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.personalinformation.PersonalInformationController;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdatePersonalInformationRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.validation.PersonalInformationFieldAccessChecker;
import com.bct.ngtpa.apiservice.adapter.in.web.validation.PersonalInformationUpdateYamlValidator;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimUpdatePersonalInformationAdapter;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateMemberInfoApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.port.in.GetPersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.application.service.account.AccountIdentifierFormatter;
import com.bct.ngtpa.apiservice.application.usecase.UpdatePersonalInformationService;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class PersonalInformationPutUpdateUnavailableContractTest {
  @ParameterizedTest
  @MethodSource("failures")
  void realPutFlowReturns503SystemErrorWithoutChangingTheResponseEnvelope(
      Mono<ApimResponseEnvelope<UpdateMemberInfoApimDataItem>> downstream) {
    var executor = mock(ApimExchangeExecutor.class);
    when(executor.postEnvelope(any())).thenAnswer(ignored -> downstream);
    var useCase =
        new UpdatePersonalInformationService(
            new ApimUpdatePersonalInformationAdapter(
                executor, new AccountIdentifierFormatter(11, 4, '0')));
    var contextResolver = mock(CurrentPortalAccessContextResolver.class);
    when(contextResolver.current())
        .thenReturn(
            Mono.just(
                new PortalAccessContext(
                    new ActorContext("actor", ActorUserRole.ROLE_USER_MBR),
                    new AccountContext(
                        "ACC", "DB", "00000000118", "0003", "DBS", "OE", TermStatus.BLANK, null))));
    var checker = mock(PersonalInformationFieldAccessChecker.class);
    when(checker.check(any(), any(), any(), any(), any())).thenReturn(Mono.just(List.of()));
    var mapper = mock(PersonalInformationUpdateWebMapper.class);
    when(mapper.toCommand(any(), any()))
        .thenReturn(new UpdatePersonalInformationCommand(true, Map.of("email", "a@b.test")));
    var controller =
        new PersonalInformationController(
            mock(GetPersonalInformationUseCase.class),
            mock(PersonalInformationWebMapper.class),
            contextResolver,
            useCase,
            mapper,
            mock(PersonalInformationUpdateResponseMapper.class),
            mock(PersonalInformationUpdateYamlValidator.class),
            exchange -> Optional.empty(),
            checker);
    var handler =
        new ApiExceptionHandler(
            new ExceptionMappingRegistryConfig().exceptionMappingRegistry(),
            new PublicMessageResolver(
                (code, locale, accountEnv, trust, scheme) -> {
                  throw new AssertionError("must not resolve a message");
                }),
            new ErrorEventLogger(
                new LoggingSanitizer(
                    new ObjectMapper(), new MaskingService(new MaskingProperties()))),
            new ErrorResponseRenderer());

    WebTestClient.bindToController(controller)
        .controllerAdvice(handler)
        .build()
        .put()
        .uri("/api/v1/personal-information")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            new UpdatePersonalInformationRequest("1.0", true, Map.of("emailAddress", "a@b.test")))
        .exchange()
        .expectStatus()
        .isEqualTo(503)
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false)
        .jsonPath("$.status")
        .isEqualTo("SYSTEM_ERROR")
        .jsonPath("$.result")
        .isEmpty()
        .jsonPath("$.messages")
        .isEmpty()
        .jsonPath("$.errors.length()")
        .isEqualTo(1)
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.personal-information.update.unavailable")
        .jsonPath("$.errors[0].type")
        .isEqualTo("SYSTEM")
        .jsonPath("$.errors[0].targets")
        .isEmpty()
        .jsonPath("$.errors[0].message")
        .doesNotExist();
  }

  static Stream<Mono<ApimResponseEnvelope<UpdateMemberInfoApimDataItem>>> failures() {
    var selected =
        UpdateMemberInfoApimDataItem.builder()
            .success(true)
            .policyNo("118")
            .certNo("3")
            .env("DB")
            .build();
    return Stream.of(
        Mono.error(new TimeoutException("private timeout")),
        envelope("private downstream error", null),
        envelope("", List.of()),
        envelope("", List.of(selected, selected)),
        envelope(
            "",
            List.of(
                UpdateMemberInfoApimDataItem.builder()
                    .success(false)
                    .policyNo("118")
                    .certNo("3")
                    .env("DB")
                    .build())),
        envelope(
            "",
            List.of(
                UpdateMemberInfoApimDataItem.builder()
                    .success(true)
                    .policyNo("118")
                    .certNo("0")
                    .env("DB")
                    .build())));
  }

  private static Mono<ApimResponseEnvelope<UpdateMemberInfoApimDataItem>> envelope(
      String error, List<UpdateMemberInfoApimDataItem> rows) {
    return Mono.just(
        ApimResponseEnvelope.<UpdateMemberInfoApimDataItem>builder()
            .response(
                ApimResponseBody.<UpdateMemberInfoApimDataItem>builder()
                    .errMessage(error)
                    .data(rows)
                    .build())
            .build());
  }
}
