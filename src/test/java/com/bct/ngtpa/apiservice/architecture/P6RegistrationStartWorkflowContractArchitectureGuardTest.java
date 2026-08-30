package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class P6RegistrationStartWorkflowContractArchitectureGuardTest {

  @Test
  void registrationStartNoLongerUsesSimpleMutationResponsePath() throws Exception {
    String controller =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/RegistrationController.java"));
    String mapper =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/mapper/RegistrationStartWebMapper.java"));

    assertThat(controller).doesNotContain("SimpleMutationResponse", "SimpleWebError");
    assertThat(mapper).doesNotContain("SimpleMutationResponse", "SimpleWebError");
  }

  @Test
  void workflowAlignedMapperUsesExistingWorkflowMutationResponseMapper() throws Exception {
    String mapper =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/mapper/RegistrationStartWebMapper.java"));

    assertThat(mapper).contains("WorkflowResult", "WorkflowMutationResponseMapper");
  }

  @Test
  void workflowAlignedControllerDoesNotDependOnOutboundAdapters() throws Exception {
    String controller =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/RegistrationController.java"));

    assertThat(controller)
        .doesNotContain(
            "adapter.out.apim",
            "adapter.out.redis",
            "adapter.out.sms",
            "adapter.out.email",
            "ApimWebClientFacade",
            "ReactiveStringRedisTemplate",
            "OtpChallengePort",
            "MessageDeliveryPort",
            "RegistrationLookupPort");
  }
}
