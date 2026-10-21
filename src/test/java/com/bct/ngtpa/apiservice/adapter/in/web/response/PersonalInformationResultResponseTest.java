package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PersonalInformationResultResponseTest {

  private static final PageResponse PAGE =
      new PageResponse("personalInformationPage", "Personal Information", "en");
  private static final FormSchemaResponse FORM =
      new FormSchemaResponse("personalInformationForm", "1.0", "view", null, null, null, null);

  @Test
  void fromCarriesPageAndFormOfTheFormPageResponseIntoData() {
    PersonalInformationResultResponse result =
        PersonalInformationResultResponse.from(FormPageResponse.success(PAGE, FORM));

    assertThat(result.data().page()).isSameAs(PAGE);
    assertThat(result.data().form()).isSameAs(FORM);
  }

  @Test
  void fromRejectsNullFormPageResponse() {
    assertThatThrownBy(() -> PersonalInformationResultResponse.from(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("formPage");
  }

  @Test
  void constructorRejectsNullData() {
    assertThatThrownBy(() -> new PersonalInformationResultResponse(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("data");
  }

  @Test
  void dataKeepsPageAndForm() {
    PersonalInformationDataResponse data = new PersonalInformationDataResponse(PAGE, FORM);

    assertThat(data.page()).isEqualTo(PAGE);
    assertThat(data.form()).isEqualTo(FORM);
  }
}
