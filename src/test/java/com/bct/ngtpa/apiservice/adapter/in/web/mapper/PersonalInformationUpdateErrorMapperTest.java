package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PersonalInformationYamlContextRunner;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationError;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersonalInformationUpdateErrorMapperTest {
  @Test
  void retiredLegacyMessageGeneratingEntryPointHasNoPublicMethod() {
    assertThat(
            Arrays.stream(PersonalInformationUpdateErrorMapper.class.getMethods())
                .map(java.lang.reflect.Method::getName))
        .isNotEmpty()
        .doesNotContain("toApiErrors");
  }

  @Test
  void mapsRequiredUnchangedAndResolvesInvalidFormatFromRealYaml() {
    PersonalInformationYamlContextRunner.create()
        .run(
            context -> {
              var mapper =
                  new PersonalInformationUpdateErrorMapper(
                      context.getBean(BffPagesProperties.class));
              var errors =
                  mapper.toMappedErrors(
                      row(
                          List.of(
                              new UpdatePersonalInformationError(
                                  "FIELD", List.of("email"), "REQUIRED"),
                              new UpdatePersonalInformationError(
                                  "FIELD", List.of("email"), "INVALID_FORMAT"))));
              assertThat(errors)
                  .extracting(com.bct.ngtpa.apiservice.adapter.in.web.response.MappedError::code)
                  .containsExactly(
                      "personalInformation.field.required", "personalInformation.email.invalid");
              assertThat(errors)
                  .allSatisfy(error -> assertThat(error.targets()).containsExactly("emailAddress"));
            });
  }

  @Test
  void mapsCrossFieldToOneErrorWithMultipleYamlTargets() {
    PersonalInformationYamlContextRunner.create()
        .run(
            context -> {
              var mapper =
                  new PersonalInformationUpdateErrorMapper(
                      context.getBean(BffPagesProperties.class));
              assertThat(
                      mapper.toMappedErrors(
                          row(
                              List.of(
                                  new UpdatePersonalInformationError(
                                      "CROSS_FIELD",
                                      List.of("addr1|addr2"),
                                      "AT_LEAST_ONE_REQUIRED")))))
                  .containsExactly(
                      new com.bct.ngtpa.apiservice.adapter.in.web.response.MappedError(
                          "CROSS_FIELD",
                          "personalInformation.address.atLeastOneRequired",
                          List.of("residentialAddressLine1", "residentialAddressLine2")));
            });
  }

  private static UpdatePersonalInformationResult row(List<UpdatePersonalInformationError> errors) {
    return new UpdatePersonalInformationResult(
        true, false, "118", "2", "DB", null, null, null, errors);
  }
}
