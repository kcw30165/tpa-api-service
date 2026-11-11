package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckContext;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckResult;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPurposePayload;
import com.bct.ngtpa.apiservice.application.usecase.passwordvalidation.PasswordValidationPurposePrecheckPolicy;
import com.bct.ngtpa.apiservice.application.usecase.passwordvalidation.PasswordValidationPurposePrecheckPolicyRegistry;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class PasswordValidationPurposePayloadDecoderRegistryTest {
  private static final String SENSITIVE_MARKER = "submitted-value-marker";
  private static final Set<PasswordValidationPurpose> ALL_PURPOSES =
      Set.copyOf(EnumSet.allOf(PasswordValidationPurpose.class));

  @Test
  void registersMatchingDecodersAndPoliciesAndLooksUpByPurpose() {
    var policyPayloadTypes = policyRegistry(ALL_PURPOSES).payloadTypes();
    var registry =
        new PasswordValidationPurposePayloadDecoderRegistry(
            List.of(
                new TestDecoder(PasswordValidationPurpose.CANCEL_INVESTMENT_CHOICE),
                new TestDecoder(PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE),
                new TestDecoder(PasswordValidationPurpose.CHANGE_INVESTMENT_CHOICE)),
            ALL_PURPOSES,
            policyPayloadTypes);

    for (var purpose : PasswordValidationPurpose.values()) {
      var first = registry.decode(purpose, JsonNodeFactory.instance.objectNode().putNull("field"));
      var second = registry.decode(purpose, JsonNodeFactory.instance.objectNode().putNull("field"));
      assertThat(first)
          .isInstanceOfSatisfying(
              TestPayload.class, payload -> assertThat(payload.purpose()).isEqualTo(purpose));
      assertThat(second)
          .isInstanceOfSatisfying(
              TestPayload.class, payload -> assertThat(payload.purpose()).isEqualTo(purpose));
    }
  }

  @Test
  void rejectsDuplicateDecoderRegistration() {
    assertThatThrownBy(
            () ->
                new PasswordValidationPurposePayloadDecoderRegistry(
                    List.of(
                        new TestDecoder(PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE),
                        new TestDecoder(PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE),
                        new TestDecoder(PasswordValidationPurpose.CHANGE_INVESTMENT_CHOICE),
                        new TestDecoder(PasswordValidationPurpose.CANCEL_INVESTMENT_CHOICE)),
                    ALL_PURPOSES,
                    policyRegistry(ALL_PURPOSES).payloadTypes()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Duplicate password-validation payload decoder registration");
  }

  @Test
  void rejectsMissingDecoderForSupportedPurpose() {
    assertThatThrownBy(
            () ->
                new PasswordValidationPurposePayloadDecoderRegistry(
                    List.of(
                        new TestDecoder(PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE),
                        new TestDecoder(PasswordValidationPurpose.CHANGE_INVESTMENT_CHOICE)),
                    ALL_PURPOSES,
                    policyRegistry(ALL_PURPOSES).payloadTypes()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing password-validation payload decoder registration");
  }

  @Test
  void rejectsPolicyRegistrationForDifferentSupportedPurposeSet() {
    assertThatThrownBy(
            () ->
                new PasswordValidationPurposePayloadDecoderRegistry(
                    List.of(
                        new TestDecoder(PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE),
                        new TestDecoder(PasswordValidationPurpose.CHANGE_INVESTMENT_CHOICE)),
                    Set.of(
                        PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE,
                        PasswordValidationPurpose.CHANGE_INVESTMENT_CHOICE),
                    Map.of(
                        PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE, TestPayload.class)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Password-validation decoder and policy purposes do not match");
  }

  @Test
  void rejectsDecoderPolicyPayloadTypeMismatch() {
    var policies =
        new PasswordValidationPurposePrecheckPolicyRegistry(
            List.of(
                new TestPolicy<>(
                    PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE, TestPayload.class),
                new TestPolicy<>(
                    PasswordValidationPurpose.CHANGE_INVESTMENT_CHOICE, OtherPayload.class),
                new TestPolicy<>(
                    PasswordValidationPurpose.CANCEL_INVESTMENT_CHOICE, TestPayload.class)),
            ALL_PURPOSES);

    assertThatThrownBy(
            () ->
                new PasswordValidationPurposePayloadDecoderRegistry(
                    allDecoders(), ALL_PURPOSES, policies.payloadTypes()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Password-validation decoder and policy payload types do not match");
  }

  @Test
  void failsClosedForPurposeOutsideDecoderRegistration() {
    var supported = Set.of(PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE);
    var registry =
        new PasswordValidationPurposePayloadDecoderRegistry(
            List.of(new TestDecoder(PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE)),
            supported,
            policyRegistry(supported).payloadTypes());

    assertThatThrownBy(
            () ->
                registry.decode(
                    PasswordValidationPurpose.CHANGE_INVESTMENT_CHOICE,
                    JsonNodeFactory.instance.objectNode()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unsupported password-validation payload decoder purpose");
  }

  @Test
  void decoderRegistryStringFormDoesNotExposeDecoderDetails() {
    var registry =
        new PasswordValidationPurposePayloadDecoderRegistry(
            allDecoders(), ALL_PURPOSES, policyRegistry(ALL_PURPOSES).payloadTypes());

    assertThat(registry.toString()).doesNotContain(SENSITIVE_MARKER);
  }

  private PasswordValidationPurposePrecheckPolicyRegistry policyRegistry(
      Set<PasswordValidationPurpose> supported) {
    return new PasswordValidationPurposePrecheckPolicyRegistry(
        supported.stream().map(purpose -> policy(purpose, TestPayload.class)).toList(), supported);
  }

  private List<PasswordValidationPurposePayloadDecoder<?>> allDecoders() {
    return List.of(
        new TestDecoder(PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE),
        new TestDecoder(PasswordValidationPurpose.CHANGE_INVESTMENT_CHOICE),
        new TestDecoder(PasswordValidationPurpose.CANCEL_INVESTMENT_CHOICE));
  }

  private static <T extends PasswordValidationPurposePayload>
      PasswordValidationPurposePrecheckPolicy<T> policy(
          PasswordValidationPurpose purpose, Class<T> payloadType) {
    return new TestPolicy<>(purpose, payloadType);
  }

  private static final class TestPayload implements PasswordValidationPurposePayload {
    private final PasswordValidationPurpose purpose;

    private TestPayload(PasswordValidationPurpose purpose) {
      this.purpose = purpose;
    }

    private PasswordValidationPurpose purpose() {
      return purpose;
    }

    @Override
    public String toString() {
      return "TestPayload[<redacted>]";
    }
  }

  private static final class OtherPayload implements PasswordValidationPurposePayload {}

  private static final class TestDecoder
      implements PasswordValidationPurposePayloadDecoder<TestPayload> {
    private final PasswordValidationPurpose purpose;

    private TestDecoder(PasswordValidationPurpose purpose) {
      this.purpose = purpose;
    }

    @Override
    public PasswordValidationPurpose purpose() {
      return purpose;
    }

    @Override
    public Class<TestPayload> payloadType() {
      return TestPayload.class;
    }

    @Override
    public TestPayload decode(JsonNode operationPayload) {
      return new TestPayload(purpose);
    }

    @Override
    public String toString() {
      return "TestDecoder[" + SENSITIVE_MARKER + "]";
    }
  }

  private static final class TestPolicy<T extends PasswordValidationPurposePayload>
      implements PasswordValidationPurposePrecheckPolicy<T> {
    private final PasswordValidationPurpose purpose;
    private final Class<T> payloadType;

    private TestPolicy(PasswordValidationPurpose purpose, Class<T> payloadType) {
      this.purpose = purpose;
      this.payloadType = payloadType;
    }

    @Override
    public PasswordValidationPurpose purpose() {
      return purpose;
    }

    @Override
    public Class<T> payloadType() {
      return payloadType;
    }

    @Override
    public Mono<PasswordValidationPrecheckResult> precheck(
        T payload, PasswordValidationPrecheckContext context) {
      return Mono.just(PasswordValidationPrecheckResult.success());
    }

    @Override
    public String toString() {
      return "TestPolicy[" + SENSITIVE_MARKER + "]";
    }
  }
}
