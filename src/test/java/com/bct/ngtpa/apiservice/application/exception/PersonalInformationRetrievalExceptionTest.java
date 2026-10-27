package com.bct.ngtpa.apiservice.application.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class PersonalInformationRetrievalExceptionTest {

  @Test
  void systemFailureCarriesAFixedValueFreeMessageAndNoCause() {
    var exception = PersonalInformationRetrievalException.systemFailure();

    assertThat(exception)
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Personal information retrieval failed")
        .hasNoCause();
  }

  @Test
  void everyCallReturnsANewInstance() {
    assertThat(PersonalInformationRetrievalException.systemFailure())
        .isNotSameAs(PersonalInformationRetrievalException.systemFailure());
  }

  @Test
  void theClassIsFinalAndCannotBeConstructedDirectly() {
    assertThat(Modifier.isFinal(PersonalInformationRetrievalException.class.getModifiers()))
        .isTrue();
    assertThat(PersonalInformationRetrievalException.class.getDeclaredConstructors())
        .allSatisfy(
            constructor -> assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue());
  }
}
