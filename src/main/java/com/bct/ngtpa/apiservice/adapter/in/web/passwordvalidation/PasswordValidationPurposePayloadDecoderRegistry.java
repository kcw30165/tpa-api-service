package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPurposePayload;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class PasswordValidationPurposePayloadDecoderRegistry {
  private static final String UNSUPPORTED_PURPOSE =
      "Unsupported password-validation payload decoder purpose";

  private final Map<
          PasswordValidationPurpose,
          PasswordValidationPurposePayloadDecoder<? extends PasswordValidationPurposePayload>>
      decoders;

  public PasswordValidationPurposePayloadDecoderRegistry(
      Collection<
              ? extends
                  PasswordValidationPurposePayloadDecoder<
                      ? extends PasswordValidationPurposePayload>>
          registeredDecoders,
      Set<PasswordValidationPurpose> supportedPurposes,
      Map<PasswordValidationPurpose, Class<? extends PasswordValidationPurposePayload>>
          policyPayloadTypes) {
    var expectedPurposes = copySupportedPurposes(supportedPurposes);
    var registeredPolicyPayloadTypes =
        Map.copyOf(
            Objects.requireNonNull(policyPayloadTypes, "policyPayloadTypes must not be null"));
    if (!registeredPolicyPayloadTypes.keySet().equals(expectedPurposes)) {
      throw new IllegalArgumentException(
          "Password-validation decoder and policy purposes do not match");
    }

    EnumMap<
            PasswordValidationPurpose,
            PasswordValidationPurposePayloadDecoder<? extends PasswordValidationPurposePayload>>
        registrations = new EnumMap<>(PasswordValidationPurpose.class);
    for (var decoder : Objects.requireNonNull(registeredDecoders, "decoders must not be null")) {
      Objects.requireNonNull(decoder, "decoder must not be null");
      var purpose = Objects.requireNonNull(decoder.purpose(), "decoder purpose must not be null");
      Objects.requireNonNull(decoder.payloadType(), "decoder payload type must not be null");
      if (!expectedPurposes.contains(purpose)) {
        throw new IllegalArgumentException(UNSUPPORTED_PURPOSE);
      }
      if (registrations.putIfAbsent(purpose, decoder) != null) {
        throw new IllegalArgumentException(
            "Duplicate password-validation payload decoder registration");
      }
    }
    if (!registrations.keySet().containsAll(expectedPurposes)) {
      throw new IllegalArgumentException(
          "Missing password-validation payload decoder registration");
    }
    for (var purpose : expectedPurposes) {
      var decoder = registrations.get(purpose);
      if (!decoder.payloadType().equals(registeredPolicyPayloadTypes.get(purpose))) {
        throw new IllegalArgumentException(
            "Password-validation decoder and policy payload types do not match");
      }
    }
    this.decoders = Collections.unmodifiableMap(registrations);
  }

  public PasswordValidationPurposePayloadDecoder<? extends PasswordValidationPurposePayload>
      require(PasswordValidationPurpose purpose) {
    Objects.requireNonNull(purpose, "purpose must not be null");
    var decoder = decoders.get(purpose);
    if (decoder == null) {
      throw new IllegalArgumentException(UNSUPPORTED_PURPOSE);
    }
    return decoder;
  }

  @Override
  public String toString() {
    return "PasswordValidationPurposePayloadDecoderRegistry[registrations=" + decoders.size() + "]";
  }

  private static Set<PasswordValidationPurpose> copySupportedPurposes(
      Set<PasswordValidationPurpose> purposes) {
    Objects.requireNonNull(purposes, "supportedPurposes must not be null");
    if (purposes.isEmpty()) {
      throw new IllegalArgumentException("At least one password-validation purpose is required");
    }
    return Collections.unmodifiableSet(EnumSet.copyOf(purposes));
  }
}
