package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.UpdatePersonalInformationResponse;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersonalInformationUpdateResultArrayArchitectureGuardTest {
  @Test
  void responseMapperReturnsEndpointEnvelopeOfMappedErrors() throws Exception {
    assertThat(PersonalInformationUpdateResponseMapper.class
        .getMethod("toResponse", List.class).getReturnType()).isEqualTo(UpdatePersonalInformationResponse.class);
    var errors = (ParameterizedType) UpdatePersonalInformationResponse.class.getDeclaredField("errors").getGenericType();
    assertThat(errors.getActualTypeArguments()[0])
        .isEqualTo(com.bct.ngtpa.apiservice.adapter.in.web.response.MappedError.class);
    assertThat(UpdatePersonalInformationResponse.class.getDeclaredField("result").getType())
        .isEqualTo(PersonalInformationUpdateResultResponse.class);
  }

  @Test
  void resultContainsDataAndDataContainsSelectedSuccessfulOthersAndFailedOthers() {
    assertThat(PersonalInformationUpdateResultResponse.class.getRecordComponents())
        .extracting(java.lang.reflect.RecordComponent::getName).containsExactly("data");
    assertThat(PersonalInformationUpdateResultResponse.Data.class.getRecordComponents())
        .extracting(java.lang.reflect.RecordComponent::getName)
        .containsExactly("selectedAccount", "rows", "errors");
  }

  @Test
  void redundantAccountDtosAreNotReintroducedInMainSources() throws Exception {
    try (var stream = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/java"))) {
      var offenders = stream.filter(path -> path.toString().endsWith(".java"))
          .filter(path -> {
            try {
              String source = java.nio.file.Files.readString(path);
              return source.contains("PersonalInformationUpdateAccountResponse")
                  || source.contains("UpdatePersonalInformationAccountResult");
            } catch (java.io.IOException error) {
              throw new IllegalStateException("Failed to read " + path, error);
            }
          }).toList();
      assertThat(offenders).isEmpty();
    }
  }
}
