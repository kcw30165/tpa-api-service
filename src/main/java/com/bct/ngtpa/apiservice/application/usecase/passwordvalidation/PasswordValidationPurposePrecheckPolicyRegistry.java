package com.bct.ngtpa.apiservice.application.usecase.passwordvalidation;

import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckContext;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckResult;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPurposePayload;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import reactor.core.publisher.Mono;

public final class PasswordValidationPurposePrecheckPolicyRegistry {
  private static final String UNSUPPORTED_PURPOSE =
      "Unsupported password-validation precheck purpose";

  private final Map<
          PasswordValidationPurpose,
          PasswordValidationPurposePrecheckPolicy<? extends PasswordValidationPurposePayload>>
      policies;
  private final Set<PasswordValidationPurpose> supportedPurposes;

  public PasswordValidationPurposePrecheckPolicyRegistry(
      Collection<
              ? extends
                  PasswordValidationPurposePrecheckPolicy<
                      ? extends PasswordValidationPurposePayload>>
          registeredPolicies,
      Set<PasswordValidationPurpose> supportedPurposes) {
    this.supportedPurposes = copySupportedPurposes(supportedPurposes);
    EnumMap<
            PasswordValidationPurpose,
            PasswordValidationPurposePrecheckPolicy<? extends PasswordValidationPurposePayload>>
        registrations = new EnumMap<>(PasswordValidationPurpose.class);
    for (var policy : Objects.requireNonNull(registeredPolicies, "policies must not be null")) {
      Objects.requireNonNull(policy, "policy must not be null");
      var purpose = Objects.requireNonNull(policy.purpose(), "policy purpose must not be null");
      Objects.requireNonNull(policy.payloadType(), "policy payload type must not be null");
      if (!this.supportedPurposes.contains(purpose)) {
        throw new IllegalArgumentException(UNSUPPORTED_PURPOSE);
      }
      if (registrations.putIfAbsent(purpose, policy) != null) {
        throw new IllegalArgumentException(
            "Duplicate password-validation precheck policy registration");
      }
    }
    if (!registrations.keySet().containsAll(this.supportedPurposes)) {
      throw new IllegalArgumentException(
          "Missing password-validation precheck policy registration");
    }
    this.policies = Collections.unmodifiableMap(registrations);
  }

  public Mono<PasswordValidationPrecheckResult> precheck(
      PasswordValidationPurpose purpose,
      PasswordValidationPurposePayload payload,
      PasswordValidationPrecheckContext context) {
    Objects.requireNonNull(payload, "payload must not be null");
    Objects.requireNonNull(purpose, "purpose must not be null");
    var policy = policies.get(purpose);
    if (policy == null) {
      throw new IllegalArgumentException(UNSUPPORTED_PURPOSE);
    }
    if (!policy.payloadType().isInstance(payload)) {
      throw new IllegalArgumentException("Password-validation policy payload type mismatch");
    }
    return precheckTyped(policy, payload, context);
  }

  public Set<PasswordValidationPurpose> supportedPurposes() {
    return Collections.unmodifiableSet(EnumSet.copyOf(supportedPurposes));
  }

  public Map<PasswordValidationPurpose, Class<? extends PasswordValidationPurposePayload>>
      payloadTypes() {
    EnumMap<PasswordValidationPurpose, Class<? extends PasswordValidationPurposePayload>>
        registrations = new EnumMap<>(PasswordValidationPurpose.class);
    policies.forEach((purpose, policy) -> registrations.put(purpose, policy.payloadType()));
    return Collections.unmodifiableMap(registrations);
  }

  @Override
  public String toString() {
    return "PasswordValidationPurposePrecheckPolicyRegistry[registrations=" + policies.size() + "]";
  }

  private static Set<PasswordValidationPurpose> copySupportedPurposes(
      Set<PasswordValidationPurpose> purposes) {
    Objects.requireNonNull(purposes, "supportedPurposes must not be null");
    if (purposes.isEmpty()) {
      throw new IllegalArgumentException("At least one password-validation purpose is required");
    }
    return Collections.unmodifiableSet(EnumSet.copyOf(purposes));
  }

  private static <T extends PasswordValidationPurposePayload>
      Mono<PasswordValidationPrecheckResult> precheckTyped(
          PasswordValidationPurposePrecheckPolicy<T> policy,
          PasswordValidationPurposePayload payload,
          PasswordValidationPrecheckContext context) {
    return policy.precheck(policy.payloadType().cast(payload), context);
  }
}
