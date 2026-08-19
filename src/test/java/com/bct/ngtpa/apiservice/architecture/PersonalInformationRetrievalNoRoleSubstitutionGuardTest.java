package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PersonalInformationRetrievalNoRoleSubstitutionGuardTest {

  private static String source(String path) throws Exception {
    return Files.readString(Path.of(path));
  }

  @Test
  void retrievalCommandsRequireExplicitRoleWithoutSubstitution() throws Exception {
    String inbound =
        source(
            "src/main/java/com/bct/ngtpa/apiservice/application/dto/GetPersonalInformationCommand.java");
    String outbound =
        source(
            "src/main/java/com/bct/ngtpa/apiservice/application/dto/FetchMemberInfoCommand.java");
    assertThat(inbound).contains("ActorUserRole actorUserRole").doesNotContain("ROLE_USER_MBR");
    assertThat(outbound).contains("ActorUserRole actorUserRole").doesNotContain("ROLE_USER_MBR");
  }

  @Test
  void trustedControllerSuppliesAndServicePropagatesRole() throws Exception {
    assertThat(
            source(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/PersonalInformationController.java"))
        .contains("portalAccessContext.actor().actorUserRole()");
    assertThat(
            source(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/GetPersonalInformationService.java"))
        .contains("command.actorUserRole()");
  }
}
