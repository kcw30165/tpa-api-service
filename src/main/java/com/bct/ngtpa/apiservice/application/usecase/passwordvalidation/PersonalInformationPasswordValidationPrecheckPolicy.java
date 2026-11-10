package com.bct.ngtpa.apiservice.application.usecase.passwordvalidation;

import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckContext;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckResult;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckViolation;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationPrecheckViolation.Kind;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PersonalInformationPasswordValidationPayload;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PersonalInformationPasswordValidationPayload.JsonKind;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class PersonalInformationPasswordValidationPrecheckPolicy
    implements PasswordValidationPurposePrecheckPolicy<
        PersonalInformationPasswordValidationPayload> {

  private static final String OPERATION_PAYLOAD = "operationPayload";

  @Override
  public PasswordValidationPurpose purpose() {
    return PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE;
  }

  @Override
  public Class<PersonalInformationPasswordValidationPayload> payloadType() {
    return PersonalInformationPasswordValidationPayload.class;
  }

  @Override
  public Mono<PasswordValidationPrecheckResult> precheck(
      PersonalInformationPasswordValidationPayload payload,
      PasswordValidationPrecheckContext context) {
    Objects.requireNonNull(payload, "payload must not be null");
    Objects.requireNonNull(context, "context must not be null");

    if (!isValid(payload)) {
      return Mono.just(
          PasswordValidationPrecheckResult.rejected(
              List.of(
                  new PasswordValidationPrecheckViolation(
                      Kind.FORM,
                      ErrorCodes.PASSWORD_VALIDATION_REQUEST_INVALID,
                      List.of(OPERATION_PAYLOAD)))));
    }
    return Mono.just(PasswordValidationPrecheckResult.success());
  }

  private static boolean isValid(PersonalInformationPasswordValidationPayload payload) {
    return payload.operationPayloadKind() == JsonKind.OBJECT
        && payload.formVersionPresent()
        && payload.formVersionKind() == JsonKind.STRING
        && payload.formVersion() != null
        && !payload.formVersion().isBlank()
        && payload.applyToAllAccountsPresent()
        && payload.applyToAllAccountsKind() == JsonKind.BOOLEAN
        && payload.applyToAllAccounts() != null
        && payload.fieldsPresent()
        && payload.fieldsKind() == JsonKind.OBJECT
        && payload.fieldsMemberCount() > 0;
  }
}
