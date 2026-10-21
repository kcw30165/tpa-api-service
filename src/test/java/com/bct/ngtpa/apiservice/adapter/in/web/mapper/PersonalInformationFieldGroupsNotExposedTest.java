package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PersonalInformationYamlContextRunner;
import com.bct.ngtpa.apiservice.adapter.in.web.response.FormPageResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.FormSchemaResponse;
import com.bct.ngtpa.apiservice.application.dto.PersonalInformationResult;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PersonalInformationFieldGroupsNotExposedTest {

  private final ApplicationContextRunner contextRunner =
      PersonalInformationYamlContextRunner.create();

  @Test
  void getResponseDoesNotExposeFieldGroupsToTheFrontend() {
    contextRunner.run(
        context -> {
          BffPagesProperties properties = context.getBean(BffPagesProperties.class);
          PersonalInformationWebMapper mapper =
              new PersonalInformationWebMapper(
                  mock(LoggingSanitizer.class),
                  properties,
                  new YamlResponseMapper(new ObjectMapper()));

          FormPageResponse<FormSchemaResponse> response =
              mapper.toFormPageResponse(new PersonalInformationResult(Map.of(), Map.of()), "en");
          String json = new ObjectMapper().writeValueAsString(response);

          assertThat(properties.getPages().get("personalInformation").getFieldGroups())
              .isNotEmpty();
          assertThat(json).contains("personalInformationPage").doesNotContain("fieldGroup");
        });
  }
}
