package com.bct.ngtpa.apiservice.application.usecase.passwordvalidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckContext;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckResult;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckViolation;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPurposePayload;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class PasswordValidationPurposePrecheckPolicyRegistryTest {
  private static final String SENSITIVE_MARKER = "submitted-value-marker";
  private static final Set<PasswordValidationPurpose> ALL_PURPOSES =
      Set.copyOf(EnumSet.allOf(PasswordValidationPurpose.class));

  @Test
  void registersOnePolicyPerSupportedPurposeAndLooksUpDeterministically() {
    var registry =
        new PasswordValidationPurposePrecheckPolicyRegistry(
            List.of(
                new TestPolicy<>(
                    PasswordValidationPurpose.CANCEL_INVESTMENT_CHOICE, TestPayload.class),
                new TestPolicy<>(
                    PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE, TestPayload.class),
                new TestPolicy<>(
                    PasswordValidationPurpose.CHANGE_INVESTMENT_CHOICE, TestPayload.class)),
            ALL_PURPOSES);

    for (var purpose : PasswordValidationPurpose.values()) {
      assertThat(registry.payloadTypes()).containsEntry(purpose, TestPayload.class);
      assertThat(registry.precheck(purpose, new TestPayload(SENSITIVE_MARKER), null).block())
          .isEqualTo(PasswordValidationPrecheckResult.success());
    }
    assertThat(registry.supportedPurposes())
        .containsExactlyInAnyOrder(PasswordValidationPurpose.values());
  }

  @Test
  void rejectsDuplicatePolicyRegistration() {
    assertThatThrownBy(
            () ->
                new PasswordValidationPurposePrecheckPolicyRegistry(
                    List.of(
                        new TestPolicy<>(
                            PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE,
                            TestPayload.class),
                        new TestPolicy<>(
                            PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE,
                            TestPayload.class)),
                    ALL_PURPOSES))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Duplicate password-validation precheck policy registration");
  }

  @Test
  void rejectsMissingPolicyForSupportedPurpose() {
    assertThatThrownBy(
            () ->
                new PasswordValidationPurposePrecheckPolicyRegistry(
                    List.of(
                        new TestPolicy<>(
                            PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE,
                            TestPayload.class),
                        new TestPolicy<>(
                            PasswordValidationPurpose.CHANGE_INVESTMENT_CHOICE, TestPayload.class)),
                    ALL_PURPOSES))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing password-validation precheck policy registration");
  }

  @Test
  void failsClosedWhenLookingUpUnsupportedPurpose() {
    var supported = Set.of(PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE);
    var registry =
        new PasswordValidationPurposePrecheckPolicyRegistry(
            List.of(
                new TestPolicy<>(
                    PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE, TestPayload.class)),
            supported);

    assertThatThrownBy(
            () ->
                registry.precheck(
                    PasswordValidationPurpose.CHANGE_INVESTMENT_CHOICE,
                    new TestPayload(SENSITIVE_MARKER),
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unsupported password-validation precheck purpose");
  }

  @Test
  void payloadContextAndResultStringFormsDoNotExposeSubmittedOrAccountValues() {
    var payload = new TestPayload(SENSITIVE_MARKER);
    var context =
        new PasswordValidationPrecheckContext(
            new AuthenticatedSessionId("sid_sensitive-marker1234"),
            new PortalAccessContext(
                new ActorContext("actor-sensitive-marker", ActorUserRole.ROLE_INPUT),
                new AccountContext(
                    "account-sensitive-marker",
                    "TEST",
                    "policy-sensitive-marker",
                    "certificate-sensitive-marker",
                    "trust-sensitive-marker",
                    null,
                    null,
                    null)),
            "request-sensitive-marker");
    var violation =
        new PasswordValidationPrecheckViolation(
            PasswordValidationPrecheckViolation.Kind.FIELD,
            "err.investment-choice.fund.unavailable",
            List.of("allocations[0].fundCode"));
    var result = PasswordValidationPrecheckResult.rejected(List.of(violation));

    assertThat(payload.toString()).doesNotContain(SENSITIVE_MARKER);
    assertThat(context.toString())
        .doesNotContain(
            "sid_sensitive-marker1234",
            "sid_sensitive-marker",
            "actor-sensitive-marker",
            "account-sensitive-marker",
            "policy-sensitive-marker",
            "certificate-sensitive-marker",
            "trust-sensitive-marker",
            "request-sensitive-marker");
    assertThat(result.toString())
        .doesNotContain("err.investment-choice", "allocations[0].fundCode", SENSITIVE_MARKER);
    assertThat(violation.toString())
        .doesNotContain("err.investment-choice", "allocations[0].fundCode", SENSITIVE_MARKER);
  }

  private record TestPayload(String value) implements PasswordValidationPurposePayload {
    @Override
    public String toString() {
      return "TestPayload[<redacted>]";
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
