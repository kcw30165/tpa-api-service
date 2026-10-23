package com.bct.ngtpa.apiservice.adapter.in.web.fundfactsheet;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.advice.ApiExceptionHandler;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorEventLogger;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorResponseRenderer;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistryConfig;
import com.bct.ngtpa.apiservice.adapter.in.web.error.PublicMessageResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.fundfactsheet.mapper.FundFactSheetWebMapper;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.FundFactSheetResult;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.FundFactSheetRowResult;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.GetFundFactSheetCommand;
import com.bct.ngtpa.apiservice.application.exception.FundFactSheetRetrievalException;
import com.bct.ngtpa.apiservice.application.port.in.GetFundFactSheetUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Route and envelope tests. Bound directly to the controller instance, like {@code
 * AccountSummaryControllerTest}: the shared {@code X-Request-Id} filter is not part of these tests,
 * so missing-header rejection is covered by the filter's own tests, not duplicated here. {@code
 * ApiExceptionHandler} is constructed exactly as {@code ApiExceptionHandlerFundFactSheetTest} does,
 * and body assertions use {@code .expectBody().jsonPath(...)}, the idiom proven by {@code
 * ReferenceDateRefreshControllerTest} and {@code UpdatePersonalInformationControllerContractTest}.
 *
 * <p>Sub-task 1.8.3 (M1 closure, A1): the {@code ApiExceptionHandler} constructor call in {@code
 * advice()} changed from (ErrorMessageResolver, LoggingSanitizer, ExceptionMappingRegistry) to
 * (ExceptionMappingRegistry, PublicMessageResolver, ErrorEventLogger, ErrorResponseRenderer). No
 * assertion changed.
 */
class FundFactSheetControllerTest {

  private static final String ROUTE = "/api/v1/fund-fact-sheet";
  private static final String ZH = "\u7532\u57fa\u91d1";
  private static final String EN_LINK = "https://example.test/en.pdf";
  private static final String ZH_LINK = "https://example.test/zh.pdf";

  private final AtomicReference<GetFundFactSheetCommand> command = new AtomicReference<>();
  private final AtomicInteger calls = new AtomicInteger();

  private GetFundFactSheetUseCase useCase(FundFactSheetResult result) {
    return cmd -> {
      calls.incrementAndGet();
      command.set(cmd);
      return Mono.just(result);
    };
  }

  private static FundFactSheetRowResult row(
      int seq, String nameEng, String nameChi, String linkEng, String linkChi) {
    return new FundFactSheetRowResult(seq, nameEng, nameChi, linkEng, linkChi);
  }

  private static FundFactSheetResult resultOf(FundFactSheetRowResult... rows) {
    return new FundFactSheetResult(List.of(rows));
  }

  private static PortalAccessContext context() {
    return new PortalAccessContext(
        new ActorContext("U", ActorUserRole.ROLE_USER_MBR),
        new AccountContext(
            "ACC-1", "JP", "00000008802", "0230", "T", "OE", TermStatus.BLANK, null));
  }

  private static CurrentPortalAccessContextResolver resolver(Mono<PortalAccessContext> current) {
    return new CurrentPortalAccessContextResolver() {
      @Override
      public Mono<PortalAccessContext> current() {
        return current;
      }

      @Override
      public Mono<PortalAccessContext> currentOrEmpty() {
        return current;
      }
    };
  }

  private static FundFactSheetController controller(GetFundFactSheetUseCase useCase) {
    return new FundFactSheetController(
        useCase, resolver(Mono.just(context())), new FundFactSheetWebMapper());
  }

  private static ApiExceptionHandler advice() {
    ErrorMessageResolver resolver = (code, locale, accountEnv, trustCode, schemeType) -> code;
    return new ApiExceptionHandler(
        new ExceptionMappingRegistryConfig().exceptionMappingRegistry(),
        new PublicMessageResolver(resolver),
        new ErrorEventLogger(
            new LoggingSanitizer(new ObjectMapper(), new MaskingService(new MaskingProperties()))),
        new ErrorResponseRenderer());
  }

  private static org.springframework.test.web.reactive.server.WebTestClient client(
      GetFundFactSheetUseCase useCase) {
    return org.springframework.test.web.reactive.server.WebTestClient.bindToController(
            controller(useCase))
        .build();
  }

  private static org.springframework.test.web.reactive.server.WebTestClient clientWithAdvice(
      GetFundFactSheetUseCase useCase) {
    return org.springframework.test.web.reactive.server.WebTestClient.bindToController(
            controller(useCase))
        .controllerAdvice(advice())
        .build();
  }

  @Test
  void returnsTheSuccessEnvelopeAsJsonWithTheRowsUnderResultData() {
    var rows = resultOf(row(10060, "Fund A", ZH, EN_LINK, ZH_LINK));

    client(useCase(rows))
        .get()
        .uri(ROUTE)
        .header("X-Request-Id", "req-1")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType("application/json")
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(true)
        .jsonPath("$.status")
        .isEqualTo("SUCCESS")
        .jsonPath("$.messages")
        .isArray()
        .jsonPath("$.messages.length()")
        .isEqualTo(0)
        .jsonPath("$.errors")
        .isArray()
        .jsonPath("$.errors.length()")
        .isEqualTo(0)
        .jsonPath("$.result.data.rows.length()")
        .isEqualTo(1)
        .jsonPath("$.result.data.rows[0].seq")
        .isEqualTo(10060)
        .jsonPath("$.result.data.rows[0].fundName")
        .isEqualTo("Fund A")
        .jsonPath("$.result.data.rows[0].fundNameEn")
        .isEqualTo("Fund A")
        .jsonPath("$.result.data.rows[0].fundNameZh")
        .isEqualTo(ZH)
        .jsonPath("$.result.data.rows[0].factsheetUrl")
        .isEqualTo(EN_LINK);
  }

  @Test
  void forwardsAllFiveTrustedValuesFromTheAccountContextAndIgnoresQueryParameters() {
    GetFundFactSheetUseCase useCase = useCase(resultOf());

    client(useCase)
        .get()
        .uri(ROUTE + "?accountEnv=XX&policyNo=999&certNo=999&schemeType=XX&lang=zh_HK")
        .header("X-Request-Id", "req-1")
        .exchange()
        .expectStatus()
        .isOk();

    assertThat(calls).hasValue(1);
    assertThat(command.get().accountEnv()).isEqualTo("JP");
    assertThat(command.get().policyNo()).isEqualTo("00000008802");
    assertThat(command.get().certNo()).isEqualTo("0230");
    assertThat(command.get().schemeType()).isEqualTo("OE");
    assertThat(command.get().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
  }

  @Test
  void withoutAcceptLanguageTheEnglishNameAndLinkAreReturned() {
    var rows = resultOf(row(1, "Fund A", ZH, EN_LINK, ZH_LINK));

    client(useCase(rows))
        .get()
        .uri(ROUTE)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.result.data.rows[0].fundName")
        .isEqualTo("Fund A")
        .jsonPath("$.result.data.rows[0].factsheetUrl")
        .isEqualTo(EN_LINK);
  }

  @Test
  void acceptLanguageZhHkReturnsTheChineseNameAndLink() {
    var rows = resultOf(row(1, "Fund A", ZH, EN_LINK, ZH_LINK));

    client(useCase(rows))
        .get()
        .uri(ROUTE)
        .header("Accept-Language", "zh-HK")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.result.data.rows[0].fundName")
        .isEqualTo(ZH)
        .jsonPath("$.result.data.rows[0].factsheetUrl")
        .isEqualTo(ZH_LINK);
  }

  @Test
  void acceptLanguageZhAloneIsTreatedAsChinese() {
    var rows = resultOf(row(1, "Fund A", ZH, null, ZH_LINK));

    client(useCase(rows))
        .get()
        .uri(ROUTE)
        .header("Accept-Language", "zh")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.result.data.rows[0].fundName")
        .isEqualTo(ZH);
  }

  @Test
  void anUnsupportedAcceptLanguageFallsBackToEnglish() {
    var rows = resultOf(row(1, "Fund A", ZH, EN_LINK, null));

    client(useCase(rows))
        .get()
        .uri(ROUTE)
        .header("Accept-Language", "fr-FR")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.result.data.rows[0].fundName")
        .isEqualTo("Fund A");
  }

  @Test
  void aNoFundResultReturnsHttp200WithAnEmptyRowsArray() {
    client(useCase(resultOf()))
        .get()
        .uri(ROUTE)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(true)
        .jsonPath("$.result.data.rows")
        .isArray()
        .jsonPath("$.result.data.rows.length()")
        .isEqualTo(0);
  }

  @Test
  void rowsAreReturnedSortedByDisplayNameThenSeq() {
    var rows =
        resultOf(
            row(3, "Zeta Fund", null, null, null),
            row(2, "alpha Fund", null, null, null),
            row(1, "Alpha Fund", null, null, null));

    client(useCase(rows))
        .get()
        .uri(ROUTE)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.result.data.rows[0].seq")
        .isEqualTo(1)
        .jsonPath("$.result.data.rows[1].seq")
        .isEqualTo(2)
        .jsonPath("$.result.data.rows[2].seq")
        .isEqualTo(3);
  }

  @Test
  void aMissingChineseNameIsWrittenAsNullAndAMissingLinkIsOmitted() {
    var rows = resultOf(row(1, "Fund A", null, null, null));

    client(useCase(rows))
        .get()
        .uri(ROUTE)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.result.data.rows[0].fundNameZh")
        .doesNotExist()
        .jsonPath("$.result.data.rows[0].factsheetUrl")
        .doesNotExist();
  }

  @Test
  void aRetrievalFailureIsAServiceUnavailableSystemErrorWithACodeAndNoMessage() {
    GetFundFactSheetUseCase failing =
        cmd -> Mono.error(FundFactSheetRetrievalException.systemFailure());

    clientWithAdvice(failing)
        .get()
        .uri(ROUTE)
        .header("X-Request-Id", "req-1")
        .exchange()
        .expectStatus()
        .isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false)
        .jsonPath("$.status")
        .isEqualTo("SYSTEM_ERROR")
        .jsonPath("$.errors.length()")
        .isEqualTo(1)
        .jsonPath("$.errors[0].type")
        .isEqualTo("SYSTEM")
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.fund-fact-sheet.retrieval.unavailable")
        .jsonPath("$.errors[0].source")
        .isEqualTo("SERVER")
        .jsonPath("$.errors[0].message")
        .doesNotExist();
  }

  @Test
  void theUseCaseIsNotCalledWhenTheContextCannotBeResolved() {
    var controller =
        new FundFactSheetController(
            useCase(resultOf()),
            resolver(Mono.error(new IllegalStateException("no context"))),
            new FundFactSheetWebMapper());

    StepVerifier.create(controller.getFundFactSheet(null))
        .expectError(IllegalStateException.class)
        .verify();

    assertThat(calls).hasValue(0);
  }

  @Test
  void theRequestHeaderContextLanguageWinsOverTheRawAcceptLanguage() {
    var rows = resultOf(row(1, "Fund A", ZH, EN_LINK, ZH_LINK));

    var response =
        controller(useCase(rows))
            .getFundFactSheet("en")
            .contextWrite(
                ctx ->
                    ctx.put(
                        RequestHeaderContextKeys.CONTEXT_KEY,
                        new RequestHeaderContext("ACC-1", "req-1", "zh-HK")))
            .block();

    assertThat(response.result().data().rows().get(0).fundName()).isEqualTo(ZH);
  }

  @Test
  void theConstructorRequiresEveryDependency() {
    var useCase = useCase(resultOf());
    var resolver = resolver(Mono.just(context()));
    var mapper = new FundFactSheetWebMapper();

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new FundFactSheetController(null, resolver, mapper))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("useCase");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new FundFactSheetController(useCase, null, mapper))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("contextResolver");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> new FundFactSheetController(useCase, resolver, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("webMapper");
  }
}
