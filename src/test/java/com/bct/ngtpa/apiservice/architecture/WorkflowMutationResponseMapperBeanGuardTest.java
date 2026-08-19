package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkflowMutationResponseMapperBeanGuardTest {

  @Test
  void workflowMutationResponseMapperHasSpringBeanConfiguration() throws Exception {
    Path config =
        Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/workflow/config/WorkflowMutationResponseMapperConfig.java");
    assertThat(Files.exists(config)).isTrue();
    String source = Files.readString(config);
    assertThat(source).contains("@Configuration", "@Bean", "WorkflowMutationResponseMapper");
  }
}
