package com.bct.ngtpa.apiservice.adapter.out.apim.downloadcorner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.adapter.out.apim.downloadcorner.dto.TrpGetDownloadCornerApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimIncidentOwnership;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimResubmissionPolicy;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.downloadcorner.DownloadCornerCommand;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.exception.DownloadCornerRetrievalException;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.infrastructure.jackson.JacksonConfig;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

class ApimDownloadCornerAdapterTest {

  private static final String PATH = "/ws/NGTPA/v1/TRPGetDownloadCorner";
  private static final String POLICY_NO = "policy-sensitive-42";
  private static final String CERT_NO = "cert-sensitive-17";
  private static final String ACTOR_ID = "actor-sensitive-88";
  private static final String REQUEST_ID = "request-id-123";
  private static final ObjectMapper JSON = new JacksonConfig().objectMapper();

  private ApimExchangeExecutor executor;
  private FailureEventPort failureEventPort;
  private List<FailureEvent> events;

  @BeforeEach
  void setUp() {
    executor = mock(ApimExchangeExecutor.class);
    failureEventPort = mock(FailureEventPort.class);
    events = new ArrayList<>();
    doAnswer(
            invocation -> {
              events.add(invocation.getArgument(0));
              return null;
            })
        .when(failureEventPort)
        .publish(any(FailureEvent.class));
  }

  @Test
  void buildsTheAccountEnvironmentReadRequestFromTheApplicationCommand() throws Exception {
    respond(
        successEnvelope(
            """
        {"env":"ignored","categories":[]}
        """));

    var properties = new DownloadCornerApimProperties();
    properties.setPath(PATH);
    var adapter = new ApimDownloadCornerAdapter(executor, failureEventPort, JSON, properties);

    StepVerifier.create(adapter.load(command()).contextWrite(requestContext()))
        .assertNext(result -> assertThat(result.categories()).isEmpty())
        .verifyComplete();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<ApimExchangeRequest<TrpGetDownloadCornerApimRequest, JsonNode>> captor =
        ArgumentCaptor.forClass(ApimExchangeRequest.class);
    org.mockito.Mockito.verify(executor).postEnvelope(captor.capture());
    ApimExchangeRequest<TrpGetDownloadCornerApimRequest, JsonNode> exchange = captor.getValue();
    assertThat(exchange.path()).isEqualTo(PATH);
    assertThat(exchange.request())
        .isEqualTo(
            new TrpGetDownloadCornerApimRequest(ACTOR_ID, "role_user_mbr", POLICY_NO, CERT_NO));
    assertThat(exchange.requestType()).isEqualTo(TrpGetDownloadCornerApimRequest.class);
    assertThat(exchange.responseDataType()).isEqualTo(JsonNode.class);
    assertThat(exchange.databaseScope()).isEqualTo(ApimDatabaseScope.ACCOUNT_ENV);
    assertThat(exchange.credentialContext().accountEnv()).isEqualTo("DB");
    assertThat(exchange.credentialContext().actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
    assertThat(exchange.retryEligibility()).isEqualTo(ApimRetryEligibility.SAFE_BUSINESS_CALL);
    assertThat(exchange.resubmissionPolicy()).isEqualTo(ApimResubmissionPolicy.ALLOW_RECOVERY);
    assertThat(exchange.incidentOwnership()).isEqualTo(ApimIncidentOwnership.TERMINAL_CALLER);
    assertThat(events).isEmpty();
  }

  @Test
  void rejectsBlankPathInTheDirectConstructor() {
    assertThatThrownBy(() -> new ApimDownloadCornerAdapter(executor, failureEventPort, JSON, " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("path must not be blank");
    assertThatThrownBy(
            () -> new ApimDownloadCornerAdapter(executor, failureEventPort, JSON, (String) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("path must not be blank");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "err.tpa.login.user.id.invalid",
        "err.tpa.cert.no.not.found",
        "err.tpa.cert.no.required"
      })
  void classifiesAccountContextMessagesAndPublishesTheRawInternalCode(String code) {
    respond(envelope(code, null));

    StepVerifier.create(adapter().load(command()).contextWrite(requestContext()))
        .expectError(DownloadCornerRetrievalException.class)
        .verify();

    assertThat(events)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.event()).isEqualTo("download-corner.retrieval.downstream_failure");
              assertThat(event.requestId()).isEqualTo(REQUEST_ID);
              assertThat(event.capability()).isEqualTo("DOWNLOAD_CORNER");
              assertThat(event.operation()).isEqualTo("TRPGetDownloadCorner");
              assertThat(event.origin()).isEqualTo(FailureOrigin.DOWNSTREAM_CONTRACT);
              assertThat(event.internalCode()).isEqualTo(code);
              assertThat(event.publicCode()).isEqualTo("err.download-corner.retrieval.unavailable");
              assertThat(event.httpStatus()).isEqualTo(503);
              assertThat(event.retryable()).isTrue();
              assertThat(event.safeDimensions())
                  .containsExactly(Map.entry("reason", "ACCOUNT_CONTEXT_MISMATCH"));
            });
  }

  @Test
  void classifiesOtherNonEmptyErrorsAsUnmappedAndPublishesTheRawInternalCode() {
    String rawError = "err.tpa.some.other.failure";
    respond(envelope(rawError, null));

    StepVerifier.create(adapter().load(command()))
        .expectError(DownloadCornerRetrievalException.class)
        .verify();

    assertThat(events)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.origin()).isEqualTo(FailureOrigin.DOWNSTREAM_CONTRACT);
              assertThat(event.internalCode()).isEqualTo(rawError);
              assertThat(event.safeDimensions())
                  .containsExactly(Map.entry("reason", "UNMAPPED_ERROR_CODE"));
            });
  }

  @Test
  void nonEmptyErrorTakesPrecedenceOverMalformedData() {
    respond(envelope("err.tpa.other", List.of(json("{\"categories\":null}"))));

    StepVerifier.create(adapter().load(command()))
        .expectError(DownloadCornerRetrievalException.class)
        .verify();

    assertThat(events)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.internalCode()).isEqualTo("err.tpa.other");
              assertThat(event.safeDimensions())
                  .containsExactly(Map.entry("reason", "UNMAPPED_ERROR_CODE"));
            });
  }

  @Test
  void treatsNullErrorMessageAsEmpty() {
    respond(envelope(null, List.of(json("{\"categories\":[]}"))));

    StepVerifier.create(adapter().load(command()))
        .assertNext(result -> assertThat(result.categories()).isEmpty())
        .verifyComplete();
    assertThat(events).isEmpty();
  }

  @Test
  void missingExecutorResponseIsMalformed() {
    when(executor.<TrpGetDownloadCornerApimRequest, JsonNode>postEnvelope(any()))
        .thenReturn(Mono.empty());

    assertMalformed("response", "MISSING_REQUIRED_FIELD");
  }

  @Test
  void reportsTheFallbackPathWhenDataCannotBeMappedToAnObject() {
    respond(successEnvelope("1"));

    assertMalformed("response.data[0]", "INVALID_TYPE");
  }

  @Test
  void nullResponseBodyIsMalformed() {
    respond(new ApimResponseEnvelope<>());

    assertMalformed("response", "MISSING_REQUIRED_FIELD");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidDataResponses")
  void missingEmptyOrNonUnitDataIsMalformed(
      String description, ApimResponseEnvelope<JsonNode> response, String fieldPath) {
    respond(response);

    assertMalformed(fieldPath, "INVALID_CARDINALITY");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("malformedCategoryAndItemFields")
  void enforcesTheE2RequiredCategoryAndItemFields(
      String description, String dataJson, String malformedField, String malformedReason) {
    respond(successEnvelope(dataJson));

    assertMalformed(malformedField, malformedReason);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource({"malformedDtoTypes", "invalidIntegerSequenceValues"})
  void mapsDtoTypeAndInvalidSequenceFailuresToMalformedResponse(
      String description, String dataJson, String malformedField) {
    respond(successEnvelope(dataJson));

    assertMalformed(malformedField, "INVALID_TYPE");
  }

  @Test
  void acceptsEmptyCategoriesAndEmptyItemsAndPreservesEmptyCategory() {
    respond(
        successEnvelope(
            """
            {"categories":[
              {"category-seq":3,"category-name-eng":"Forms","category-name-chi":"","items":[]}
            ]}
            """));

    StepVerifier.create(adapter().load(command()))
        .assertNext(
            result -> {
              assertThat(result.categories()).hasSize(1);
              assertThat(result.categories().getFirst().categorySeq()).isEqualTo(3);
              assertThat(result.categories().getFirst().categoryNameEng()).isEqualTo("Forms");
              assertThat(result.categories().getFirst().categoryNameChi()).isEqualTo("");
              assertThat(result.categories().getFirst().items()).isEmpty();
            })
        .verifyComplete();
    assertThat(events).isEmpty();
  }

  @Test
  void acceptsAnEmptyCategoriesArray() {
    respond(successEnvelope("{\"categories\":[]}"));

    StepVerifier.create(adapter().load(command()))
        .assertNext(result -> assertThat(result.categories()).isEmpty())
        .verifyComplete();
    assertThat(events).isEmpty();
  }

  @Test
  void mapsAbsentAndNullOptionalBilingualItemFieldsWithoutFailure() {
    respond(
        successEnvelope(
            """
            {"categories":[
              {"category-seq":1,"category-name-eng":"Forms","items":[
                {"item-seq":2,"item-name-eng":null,"item-link-eng":null,"file-name-eng":null,
                 "item-name-chi":null}
              ]}
            ]}
            """));

    StepVerifier.create(adapter().load(command()))
        .assertNext(
            result -> {
              var category = result.categories().getFirst();
              var item = category.items().getFirst();
              assertThat(category.categoryNameChi()).isNull();
              assertThat(item.itemNameEng()).isNull();
              assertThat(item.itemLinkEng()).isNull();
              assertThat(item.fileNameEng()).isNull();
              assertThat(item.itemNameChi()).isNull();
              assertThat(item.itemLinkChi()).isNull();
              assertThat(item.fileNameChi()).isNull();
            })
        .verifyComplete();
    assertThat(events).isEmpty();
  }

  @Test
  void acceptsDecimalSequenceValuesThatAreExactIntegersWithinIntRange() {
    respond(
        successEnvelope(
            """
            {"categories":[
              {"category-seq":10010.0,"category-name-eng":"Forms","items":[
                {"item-seq":10010.0}
              ]}
            ]}
            """));

    StepVerifier.create(adapter().load(command()))
        .assertNext(
            result -> {
              assertThat(result.categories().getFirst().categorySeq()).isEqualTo(10010);
              assertThat(result.categories().getFirst().items().getFirst().itemSeq())
                  .isEqualTo(10010);
            })
        .verifyComplete();
    assertThat(events).isEmpty();
  }

  @Test
  void wrapsTransportFailureAndPublishesItsTechnicalOrigin() {
    when(executor.<TrpGetDownloadCornerApimRequest, JsonNode>postEnvelope(any()))
        .thenReturn(Mono.error(new TimeoutException("sensitive downstream text")));

    StepVerifier.create(adapter().load(command()))
        .expectError(DownloadCornerRetrievalException.class)
        .verify();

    assertThat(events)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.origin()).isEqualTo(FailureOrigin.DOWNSTREAM_TRANSPORT);
              assertThat(event.internalCode()).isNull();
              assertThat(event.toString()).doesNotContain("sensitive downstream text");
            });
  }

  @ParameterizedTest
  @MethodSource("otherTechnicalFailures")
  void mapsOtherTechnicalFailureCategories(Throwable failure, FailureOrigin origin) {
    when(executor.<TrpGetDownloadCornerApimRequest, JsonNode>postEnvelope(any()))
        .thenReturn(Mono.error(failure));

    StepVerifier.create(adapter().load(command()))
        .expectError(DownloadCornerRetrievalException.class)
        .verify();

    assertThat(events)
        .singleElement()
        .satisfies(event -> assertThat(event.origin()).isEqualTo(origin));
  }

  @Test
  void preservesAnExistingFeatureException() {
    var failure = DownloadCornerRetrievalException.systemFailure();
    when(executor.<TrpGetDownloadCornerApimRequest, JsonNode>postEnvelope(any()))
        .thenReturn(Mono.error(failure));

    StepVerifier.create(adapter().load(command()))
        .expectErrorSatisfies(actual -> assertThat(actual).isSameAs(failure))
        .verify();
    assertThat(events)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.origin()).isEqualTo(FailureOrigin.LOCAL_SYSTEM);
              assertThat(event.safeDimensions())
                  .containsExactly(Map.entry("reason", "LOCAL_SYSTEM"));
            });
  }

  private void assertMalformed(String fieldPath, String malformedReason) {
    StepVerifier.create(adapter().load(command()).contextWrite(requestContext()))
        .expectError(DownloadCornerRetrievalException.class)
        .verify();

    assertThat(events)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.origin()).isEqualTo(FailureOrigin.DOWNSTREAM_INVALID_RESPONSE);
              assertThat(event.internalCode()).isNull();
              assertThat(event.publicCode()).isEqualTo("err.download-corner.retrieval.unavailable");
              assertThat(event.httpStatus()).isEqualTo(503);
              assertThat(event.safeDimensions())
                  .containsEntry("reason", "MALFORMED_RESPONSE")
                  .containsEntry("malformedField", fieldPath)
                  .containsEntry("malformedReason", malformedReason)
                  .containsOnlyKeys("reason", "malformedField", "malformedReason");
            });
  }

  private ApimDownloadCornerAdapter adapter() {
    return new ApimDownloadCornerAdapter(executor, failureEventPort, JSON, PATH);
  }

  private static DownloadCornerCommand command() {
    return new DownloadCornerCommand(
        "DB", POLICY_NO, CERT_NO, ACTOR_ID, ActorUserRole.ROLE_USER_MBR);
  }

  private static Context requestContext() {
    return Context.of(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, REQUEST_ID);
  }

  private void respond(ApimResponseEnvelope<JsonNode> response) {
    when(executor.<TrpGetDownloadCornerApimRequest, JsonNode>postEnvelope(any()))
        .thenReturn(Mono.just(response));
  }

  private static ApimResponseEnvelope<JsonNode> successEnvelope(String dataJson) {
    return envelope("", List.of(json(dataJson)));
  }

  private static ApimResponseEnvelope<JsonNode> envelope(String errMessage, List<JsonNode> data) {
    return new ApimResponseEnvelope<>(new ApimResponseBody<>(errMessage, data));
  }

  private static JsonNode json(String value) {
    try {
      return JSON.readTree(value);
    } catch (Exception exception) {
      throw new AssertionError(exception);
    }
  }

  static List<Arguments> invalidDataResponses() {
    return List.of(
        Arguments.of("missing data", envelope("", null), "response.data"),
        Arguments.of("empty data", envelope("", List.of()), "response.data"),
        Arguments.of(
            "multiple data entries",
            envelope("", List.of(json("{}"), json("{}"))),
            "response.data"),
        Arguments.of(
            "null sole data entry", envelope("", Arrays.asList((JsonNode) null)), "response.data"));
  }

  static List<Arguments> malformedCategoryAndItemFields() {
    return List.of(
        Arguments.of(
            "categories missing", "{}", "response.data[0].categories", "MISSING_REQUIRED_FIELD"),
        Arguments.of(
            "categories null",
            "{\"categories\":null}",
            "response.data[0].categories",
            "MISSING_REQUIRED_FIELD"),
        Arguments.of(
            "nested items missing",
            "{\"categories\":[{\"category-seq\":1,\"category-name-eng\":\"Forms\"}]}",
            "response.data[0].categories[0].items",
            "MISSING_REQUIRED_FIELD"),
        Arguments.of(
            "nested items null",
            "{\"categories\":[{\"category-seq\":1,\"category-name-eng\":\"Forms\",\"items\":null}]}",
            "response.data[0].categories[0].items",
            "MISSING_REQUIRED_FIELD"),
        Arguments.of(
            "category sequence missing",
            "{\"categories\":[{\"category-name-eng\":\"Forms\",\"items\":[]}]}",
            "response.data[0].categories[0].category-seq",
            "MISSING_REQUIRED_FIELD"),
        Arguments.of(
            "category sequence null",
            "{\"categories\":[{\"category-seq\":null,\"category-name-eng\":\"Forms\",\"items\":[]}]}",
            "response.data[0].categories[0].category-seq",
            "MISSING_REQUIRED_FIELD"),
        Arguments.of(
            "category sequence nonnumeric",
            "{\"categories\":[{\"category-seq\":\"x\",\"category-name-eng\":\"Forms\",\"items\":[]}]}",
            "response.data[0].categories[0].category-seq",
            "INVALID_TYPE"),
        Arguments.of(
            "item sequence missing",
            "{\"categories\":[{\"category-seq\":1,\"category-name-eng\":\"Forms\",\"items\":[{}]}]}",
            "response.data[0].categories[0].items[0].item-seq",
            "MISSING_REQUIRED_FIELD"),
        Arguments.of(
            "item sequence null",
            "{\"categories\":[{\"category-seq\":1,\"category-name-eng\":\"Forms\",\"items\":[{\"item-seq\":null}]}]}",
            "response.data[0].categories[0].items[0].item-seq",
            "MISSING_REQUIRED_FIELD"),
        Arguments.of(
            "item sequence nonnumeric",
            "{\"categories\":[{\"category-seq\":1,\"category-name-eng\":\"Forms\",\"items\":[{\"item-seq\":\"x\"}]}]}",
            "response.data[0].categories[0].items[0].item-seq",
            "INVALID_TYPE"),
        Arguments.of(
            "English category name missing",
            "{\"categories\":[{\"category-seq\":1,\"items\":[]}]}",
            "response.data[0].categories[0].category-name-eng",
            "MISSING_REQUIRED_FIELD"),
        Arguments.of(
            "English category name null",
            "{\"categories\":[{\"category-seq\":1,\"category-name-eng\":null,\"items\":[]}]}",
            "response.data[0].categories[0].category-name-eng",
            "MISSING_REQUIRED_FIELD"),
        Arguments.of(
            "English category name blank",
            "{\"categories\":[{\"category-seq\":1,\"category-name-eng\":\"  \",\"items\":[]}]}",
            "response.data[0].categories[0].category-name-eng",
            "REQUIRED_TEXT_BLANK"));
  }

  static List<Arguments> malformedDtoTypes() {
    return List.of(
        Arguments.of(
            "category DTO has wrong JSON type",
            "{\"categories\":[1]}",
            "response.data[0].categories[0]"),
        Arguments.of(
            "English category name has wrong JSON type",
            "{\"categories\":[{\"category-seq\":1,\"category-name-eng\":12,\"items\":[]}]}",
            "response.data[0].categories[0].category-name-eng"),
        Arguments.of(
            "English item field has wrong JSON type",
            "{\"categories\":[{\"category-seq\":1,\"category-name-eng\":\"Forms\",\"items\":["
                + "{\"item-seq\":1,\"item-link-eng\":true}]}]}",
            "response.data[0].categories[0].items[0].item-link-eng"));
  }

  static List<Arguments> invalidIntegerSequenceValues() {
    return List.of(
        Arguments.of(
            "fractional category sequence",
            "{\"categories\":[{\"category-seq\":10010.5,\"category-name-eng\":\"Forms\",\"items\":[]}]}",
            "response.data[0].categories[0].category-seq"),
        Arguments.of(
            "category sequence above int max",
            "{\"categories\":[{\"category-seq\":2147483648,\"category-name-eng\":\"Forms\",\"items\":[]}]}",
            "response.data[0].categories[0].category-seq"),
        Arguments.of(
            "category sequence below int min",
            "{\"categories\":[{\"category-seq\":-2147483649,\"category-name-eng\":\"Forms\",\"items\":[]}]}",
            "response.data[0].categories[0].category-seq"),
        Arguments.of(
            "fractional item sequence",
            "{\"categories\":[{\"category-seq\":1,\"category-name-eng\":\"Forms\",\"items\":[{\"item-seq\":10010.5}]}]}",
            "response.data[0].categories[0].items[0].item-seq"),
        Arguments.of(
            "item sequence above int max",
            "{\"categories\":[{\"category-seq\":1,\"category-name-eng\":\"Forms\",\"items\":[{\"item-seq\":2147483648}]}]}",
            "response.data[0].categories[0].items[0].item-seq"),
        Arguments.of(
            "item sequence below int min",
            "{\"categories\":[{\"category-seq\":1,\"category-name-eng\":\"Forms\",\"items\":[{\"item-seq\":-2147483649}]}]}",
            "response.data[0].categories[0].items[0].item-seq"));
  }

  static List<Arguments> otherTechnicalFailures() {
    return List.of(
        Arguments.of(
            new ApimException(
                HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID, "invalid APIM response"),
            FailureOrigin.DOWNSTREAM_CONTRACT),
        Arguments.of(
            new ApimCryptoException("invalid APIM payload"),
            FailureOrigin.DOWNSTREAM_INVALID_RESPONSE),
        Arguments.of(new IllegalStateException("local failure"), FailureOrigin.LOCAL_SYSTEM));
  }
}
