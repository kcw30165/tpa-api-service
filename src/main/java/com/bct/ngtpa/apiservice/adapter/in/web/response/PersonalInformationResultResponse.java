package com.bct.ngtpa.apiservice.adapter.in.web.response;

import java.util.Objects;

/**
 * Wraps {@link PersonalInformationDataResponse} under a {@code data} field, so that the public body
 * is {@code {success, status, result: {data: {page, form}}, messages, errors}}.
 */
public record PersonalInformationResultResponse(PersonalInformationDataResponse data) {

  public PersonalInformationResultResponse {
    Objects.requireNonNull(data, "data must not be null");
  }

  public static PersonalInformationResultResponse from(
      FormPageResponse<FormSchemaResponse> formPage) {
    Objects.requireNonNull(formPage, "formPage must not be null");
    return new PersonalInformationResultResponse(
        new PersonalInformationDataResponse(formPage.page(), formPage.form()));
  }
}
