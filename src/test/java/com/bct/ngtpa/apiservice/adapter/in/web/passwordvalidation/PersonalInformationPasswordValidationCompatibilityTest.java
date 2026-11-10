package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckContext;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckResult;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckViolation;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PersonalInformationPasswordValidationPayload;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.usecase.passwordvalidation.PersonalInformationPasswordValidationPrecheckPolicy;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.test.StepVerifier;

class PersonalInformationPasswordValidationCompatibilityTest {
  private static final String PURPOSE = "PERSONAL_INFORMATION_UPDATE";
  private static final String OPERATION_PAYLOAD = "operationPayload";
  private static final String FORM_VERSION = "\"formVersion\":\"version-marker\"";
  private static final String APPLY_TRUE = "\"applyToAllAccounts\":true";
  private static final String APPLY_FALSE = "\"applyToAllAccounts\":false";
  private static final String FIELDS = "\"fields\":{\"unknown-field\":null}";
  private static final String PASSWORD = "test-password";
  private static final String SENSITIVE_MARKER = "version-marker";
  private static final JsonFactory STRICT_JSON_FACTORY =
      JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
  private static final ObjectMapper JSON = new ObjectMapper(STRICT_JSON_FACTORY);
  private static final PasswordValidationPrecheckContext CONTEXT =
      new PasswordValidationPrecheckContext(
          new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx"),
          new PortalAccessContext(
              new ActorContext("test-actor", ActorUserRole.ROLE_INPUT),
              new AccountContext(
                  "test-account", "TEST", "test-policy", null, null, null, null, null)),
          "test-request");

  private final PersonalInformationPasswordValidationPayloadDecoder decoder =
      new PersonalInformationPasswordValidationPayloadDecoder();
  private final PersonalInformationPasswordValidationPrecheckPolicy policy =
      new PersonalInformationPasswordValidationPrecheckPolicy();
  private final PasswordValidationRequestParser parser =
      new PasswordValidationRequestParser(
          PasswordValidationTestRegistrations.decoders(), JSON, 3, 20);

  @ParameterizedTest(name = "{0}")
  @MethodSource("piPayloadCases")
  void existingPiContractCorpusMatchesCompatibilityPolicy(
      String caseName, String request, boolean expectedAccepted) {
    PrecheckDecision newDecision = strictDecoderAndPolicyDecision(request);

    assertThat(newDecision.accepted())
        .as(caseName + " compatibility policy")
        .isEqualTo(expectedAccepted);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rejectedPiPayloadCases")
  void rejectedPiPayloadUsesOnlyTheExistingGenericInvalidOutcome(String caseName, String request) {
    PrecheckDecision decision = strictDecoderAndPolicyDecision(request);

    assertThat(decision.accepted()).as(caseName).isFalse();
    assertThat(decision.result()).isNotNull();
    assertThat(decision.result().violations())
        .containsExactly(
            new PasswordValidationPrecheckViolation(
                PasswordValidationPrecheckViolation.Kind.FORM,
                ErrorCodes.PASSWORD_VALIDATION_REQUEST_INVALID,
                List.of(OPERATION_PAYLOAD)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("strictBoundaryRequests")
  void strictRawParsingRejectsMalformedDuplicateAndTrailingRequests(
      String caseName, String request) {
    assertThat(parserRejects(request)).as(caseName + " strict parser").isTrue();
    PrecheckDecision decision = strictDecoderAndPolicyDecision(request);
    assertThat(decision.accepted()).as(caseName + " strict JsonNode boundary").isFalse();
    assertThat(decision.result()).as(caseName + " rejected before decoder").isNull();
  }

  @Test
  void decoderAndPolicyDeclareTheSamePurposeAndPayloadType() {
    assertThat(decoder.purpose()).isEqualTo(policy.purpose());
    assertThat(decoder.purpose().name()).isEqualTo(PURPOSE);
    assertThat(decoder.payloadType()).isEqualTo(policy.payloadType());
  }

  @Test
  void decoderPreservesPresenceKindsAndUnknownMemberNames() throws JsonProcessingException {
    JsonNode operationPayload =
        JSON.readTree(
            """
            {"formVersion":null,"applyToAllAccounts":"false","fields":{"field-a":1},"extra":true}
            """);

    var payload = decoder.decode(operationPayload);

    assertThat(payload.operationPayloadKind())
        .isEqualTo(PersonalInformationPasswordValidationPayload.JsonKind.OBJECT);
    assertThat(payload.formVersionPresent()).isTrue();
    assertThat(payload.formVersionKind())
        .isEqualTo(PersonalInformationPasswordValidationPayload.JsonKind.NULL);
    assertThat(payload.applyToAllAccountsPresent()).isTrue();
    assertThat(payload.applyToAllAccountsKind())
        .isEqualTo(PersonalInformationPasswordValidationPayload.JsonKind.STRING);
    assertThat(payload.fieldsPresent()).isTrue();
    assertThat(payload.fieldsKind())
        .isEqualTo(PersonalInformationPasswordValidationPayload.JsonKind.OBJECT);
    assertThat(payload.fieldsMemberCount()).isEqualTo(1);
    assertThat(payload.operationMemberNames())
        .containsExactly("formVersion", "applyToAllAccounts", "fields", "extra");
    assertThat(payload.fieldsMemberNames()).containsExactly("field-a");
  }

  @Test
  void payloadPolicyAndResultStringFormsDoNotExposeSubmittedValues() {
    var payload = decoder.decode(JSON.createObjectNode().put("formVersion", SENSITIVE_MARKER));
    var rejected =
        new PasswordValidationPrecheckViolation(
            PasswordValidationPrecheckViolation.Kind.FORM,
            ErrorCodes.PASSWORD_VALIDATION_REQUEST_INVALID,
            List.of(OPERATION_PAYLOAD));
    var result = PasswordValidationPrecheckResult.rejected(List.of(rejected));

    assertThat(payload.toString()).doesNotContain(SENSITIVE_MARKER);
    assertThat(decoder.toString()).doesNotContain(SENSITIVE_MARKER);
    assertThat(policy.toString()).doesNotContain(SENSITIVE_MARKER);
    assertThat(CONTEXT.toString()).isEqualTo("PasswordValidationPrecheckContext[<redacted>]");
    assertThat(result.toString())
        .doesNotContain(SENSITIVE_MARKER, ErrorCodes.PASSWORD_VALIDATION_REQUEST_INVALID);
    assertThat(rejected.toString())
        .doesNotContain(SENSITIVE_MARKER, ErrorCodes.PASSWORD_VALIDATION_REQUEST_INVALID);
  }

  private static Stream<Arguments> piPayloadCases() {
    return piCases()
        .map(testCase -> Arguments.of(testCase.name(), testCase.request(), testCase.accepted()));
  }

  private static Stream<Arguments> rejectedPiPayloadCases() {
    return piCases()
        .filter(testCase -> !testCase.accepted())
        .map(testCase -> Arguments.of(testCase.name(), testCase.request()));
  }

  private static Stream<Arguments> strictBoundaryRequests() {
    String validRequest = request(operation(FORM_VERSION, APPLY_FALSE, FIELDS));
    return Stream.of(
        Arguments.of("malformed operation", request("{\"formVersion\":")),
        Arguments.of(
            "duplicate known operation member",
            request(operation(FORM_VERSION, "\"formVersion\":\"other\"", APPLY_FALSE, FIELDS))),
        Arguments.of(
            "duplicate unknown operation member",
            request(operation(FORM_VERSION, APPLY_FALSE, FIELDS, "\"extra\":1", "\"extra\":2"))),
        Arguments.of(
            "duplicate field member",
            request(operation(FORM_VERSION, APPLY_FALSE, "\"fields\":{\"same\":1,\"same\":2}"))),
        Arguments.of("trailing request token", validRequest + " {}"));
  }

  private static Stream<PiCase> piCases() {
    return Stream.of(
        new PiCase(
            "existing valid shape with unknown members",
            request(operation(FORM_VERSION, APPLY_TRUE, FIELDS, "\"extra\":[1.000]")),
            true),
        new PiCase(
            "valid apply-to-all false",
            request(operation(FORM_VERSION, APPLY_FALSE, FIELDS)),
            true),
        new PiCase(
            "valid apply-to-all true", request(operation(FORM_VERSION, APPLY_TRUE, FIELDS)), true),
        new PiCase("null operation root", request("null"), false),
        new PiCase("array operation root", request("[]"), false),
        new PiCase("string operation root", request("\"body\""), false),
        new PiCase("empty operation object", request("{}"), false),
        new PiCase("missing form version", request(operation(APPLY_FALSE, FIELDS)), false),
        new PiCase(
            "null form version",
            request(operation("\"formVersion\":null", APPLY_FALSE, FIELDS)),
            false),
        new PiCase(
            "wrong-kind form version",
            request(operation("\"formVersion\":1", APPLY_FALSE, FIELDS)),
            false),
        new PiCase(
            "blank form version",
            request(operation("\"formVersion\":\"  \"", APPLY_FALSE, FIELDS)),
            false),
        new PiCase("missing apply-to-all", request(operation(FORM_VERSION, FIELDS)), false),
        new PiCase(
            "null apply-to-all",
            request(operation(FORM_VERSION, "\"applyToAllAccounts\":null", FIELDS)),
            false),
        new PiCase(
            "wrong-kind apply-to-all",
            request(operation(FORM_VERSION, "\"applyToAllAccounts\":\"false\"", FIELDS)),
            false),
        new PiCase("missing fields", request(operation(FORM_VERSION, APPLY_FALSE)), false),
        new PiCase(
            "null fields", request(operation(FORM_VERSION, APPLY_FALSE, "\"fields\":null")), false),
        new PiCase(
            "wrong-kind fields array",
            request(operation(FORM_VERSION, APPLY_FALSE, "\"fields\":[]")),
            false),
        new PiCase(
            "wrong-kind fields string",
            request(operation(FORM_VERSION, APPLY_FALSE, "\"fields\":\"value\"")),
            false),
        new PiCase(
            "empty fields object",
            request(operation(FORM_VERSION, APPLY_FALSE, "\"fields\":{}")),
            false),
        new PiCase(
            "unknown field values remain accepted",
            request(
                operation(
                    FORM_VERSION,
                    APPLY_FALSE,
                    "\"fields\":{\"unknown\":null,\"nested\":{\"items\":[true,1.5]}}",
                    "\"unknownOperation\":{\"value\":false}")),
            true));
  }

  private static String operation(String... members) {
    return "{" + String.join(",", members) + "}";
  }

  private static String request(String operation) {
    return "{\"password\":\""
        + PASSWORD
        + "\",\"purpose\":\""
        + PURPOSE
        + "\",\"operationPayload\":"
        + operation
        + "}";
  }

  private boolean parserRejects(String request) {
    try {
      parser.parse(request);
      return false;
    } catch (PasswordValidationRequestInvalidException exception) {
      return true;
    }
  }

  private PrecheckDecision strictDecoderAndPolicyDecision(String request) {
    try (JsonParser parser = STRICT_JSON_FACTORY.createParser(request)) {
      JsonNode envelope = JSON.readTree(parser);
      if (envelope == null || parser.nextToken() != null) {
        return new PrecheckDecision(false, null);
      }
      var payload = decoder.decode(envelope.get(OPERATION_PAYLOAD));
      var result = new AtomicReference<PasswordValidationPrecheckResult>();
      StepVerifier.create(policy.precheck(payload, CONTEXT))
          .assertNext(result::set)
          .verifyComplete();
      return new PrecheckDecision(result.get().isSuccess(), result.get());
    } catch (IOException exception) {
      return new PrecheckDecision(false, null);
    }
  }

  private record PiCase(String name, String request, boolean accepted) {}

  private record PrecheckDecision(boolean accepted, PasswordValidationPrecheckResult result) {}
}
