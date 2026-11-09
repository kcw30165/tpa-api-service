package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.*;
import org.junit.jupiter.api.Test;

class M3R1bUsernameTerminalOwnershipArchitectureTest {
  private static final Path MAIN = Path.of("src/main/java");

  @Test
  void registrationUsernameAdapterAloneActivatesOwnershipAndDropsAspect() throws Exception {
    String adapter =
        read(
            "com/bct/ngtpa/apiservice/adapter/out/apim/registration/ApimRegistrationUsernameAvailabilityAdapter.java");
    assertThat(adapter)
        .contains("ApimIncidentOwnership.TERMINAL_CALLER")
        .doesNotContain("@LogExecution");
    var opted =
        Files.walk(MAIN)
            .filter(p -> p.toString().endsWith(".java"))
            .filter(p -> read(p).contains("ApimIncidentOwnership.TERMINAL_CALLER"))
            .map(p -> MAIN.relativize(p).toString().replace('\\', '/'))
            .toList();
    assertThat(opted)
        .contains(
            "com/bct/ngtpa/apiservice/adapter/out/apim/registration/ApimRegistrationUsernameAvailabilityAdapter.java");
    assertThat(
            read(
                "com/bct/ngtpa/apiservice/adapter/out/apim/forgotpassword/ApimForgotPasswordUsernameLookupAdapter.java"))
        .doesNotContain("TERMINAL_CALLER");
  }

  @Test
  void serviceHasOneTerminalErrorAndLocalMarker() throws Exception {
    String service =
        read(
            "com/bct/ngtpa/apiservice/application/usecase/auth/registration/username/CheckRegistrationUsernameService.java");
    assertThat(count(service, "log.error(")).isEqualTo(1);
    assertThat(service)
        .contains(
            "APIM_TECHNICAL", "LOCAL_STATE_FAILURE", "Mono.defer", "LocalUsernameStateFailure")
        .doesNotContain("getMessage()");
  }

  private static String read(String p) {
    return read(MAIN.resolve(p));
  }

  private static String read(Path p) {
    try {
      return Files.readString(p);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static int count(String s, String t) {
    int c = 0, i = 0;
    while ((i = s.indexOf(t, i)) >= 0) {
      c++;
      i += t.length();
    }
    return c;
  }
}
