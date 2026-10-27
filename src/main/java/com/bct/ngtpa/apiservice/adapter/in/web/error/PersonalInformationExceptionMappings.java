package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.exception.InvalidPersonalInformationUpdateException;
import com.bct.ngtpa.apiservice.application.exception.PersonalInformationRetrievalException;
import com.bct.ngtpa.apiservice.application.exception.PersonalInformationUpdateException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Mapping for {@link InvalidPersonalInformationUpdateException}. Built via {@link
 * ErrorDescriptor#singleWithoutMessage} for endpoint-owned code-only errors.
 */
public final class PersonalInformationExceptionMappings {

  private PersonalInformationExceptionMappings() {}

  public static ExceptionMapping<PersonalInformationUpdateException> updateUnavailable() {
    return new ExceptionMapping<>() {
      @Override
      public Class<PersonalInformationUpdateException> exceptionType() {
        return PersonalInformationUpdateException.class;
      }

      @Override
      public ErrorDescriptor describe(
          PersonalInformationUpdateException exception, ErrorContext context) {
        return ErrorDescriptor.singleWithoutMessage(
            HttpStatus.SERVICE_UNAVAILABLE,
            ApiStatus.SYSTEM_ERROR,
            "SYSTEM",
            ErrorCodes.PERSONAL_INFORMATION_UPDATE_UNAVAILABLE,
            List.of(),
            "http.api.error",
            LogLevel.ERROR,
            false);
      }
    };
  }

  public static ExceptionMapping<InvalidPersonalInformationUpdateException> invalidUpdate() {
    return new ExceptionMapping<>() {
      @Override
      public Class<InvalidPersonalInformationUpdateException> exceptionType() {
        return InvalidPersonalInformationUpdateException.class;
      }

      @Override
      public ErrorDescriptor describe(
          InvalidPersonalInformationUpdateException exception, ErrorContext context) {
        return ErrorDescriptor.singleWithoutMessage(
            HttpStatus.BAD_REQUEST,
            ApiStatus.VALIDATION_FAILED,
            "FORM",
            ErrorCodes.PERSONAL_INFORMATION_UPDATE_REQUEST_INVALID,
            List.of(),
            "http.api.error",
            LogLevel.WARN,
            false);
      }
    };
  }

  /**
   * Decision O-1: a failed member-info lookup for the personal-information PUT is exposed as one
   * value-free system error. Message resolution is deliberately skipped so downstream detail cannot
   * leak into the public response.
   */
  public static ExceptionMapping<PersonalInformationRetrievalException> retrievalUnavailable() {
    return new ExceptionMapping<>() {
      @Override
      public Class<PersonalInformationRetrievalException> exceptionType() {
        return PersonalInformationRetrievalException.class;
      }

      @Override
      public ErrorDescriptor describe(
          PersonalInformationRetrievalException exception, ErrorContext context) {
        return ErrorDescriptor.singleWithoutMessage(
            HttpStatus.SERVICE_UNAVAILABLE,
            ApiStatus.SYSTEM_ERROR,
            "SYSTEM",
            ErrorCodes.PERSONAL_INFORMATION_RETRIEVAL_UNAVAILABLE,
            List.of(),
            "http.api.error",
            LogLevel.ERROR,
            false);
      }
    };
  }
}
