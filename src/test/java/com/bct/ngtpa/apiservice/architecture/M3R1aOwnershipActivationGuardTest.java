package com.bct.ngtpa.apiservice.architecture;
import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*; import java.util.List; import org.junit.jupiter.api.Test;
class M3R1aOwnershipActivationGuardTest {
 @Test void onlyRegistrationUsernameOptsInAtR1bCheckpoint() throws Exception {
  var root=Path.of("src/main/java");
  var hits=Files.walk(root).filter(p->p.toString().endsWith(".java")).filter(p->read(p).contains("ApimIncidentOwnership.TERMINAL_CALLER")).map(p->root.relativize(p).toString().replace('\\','/')).sorted().toList();
  assertThat(hits).containsExactlyElementsOf(List.of("com/bct/ngtpa/apiservice/adapter/out/apim/ApimWebClientFacade.java","com/bct/ngtpa/apiservice/adapter/out/apim/execution/ApimExchangeOutcomeLogger.java","com/bct/ngtpa/apiservice/adapter/out/apim/registration/ApimRegistrationUsernameAvailabilityAdapter.java").stream().sorted().toList());
  String reg=read(root.resolve("com/bct/ngtpa/apiservice/adapter/out/apim/registration/ApimRegistrationUsernameAvailabilityAdapter.java"));
  assertThat(reg).contains("TERMINAL_CALLER").doesNotContain("@LogExecution");
  String forgotPassword =
      read(
          root.resolve(
              "com/bct/ngtpa/apiservice/adapter/out/apim/forgotpassword/"
                  + "ApimForgotPasswordUsernameLookupAdapter.java"));
  assertThat(forgotPassword).doesNotContain("TERMINAL_CALLER");
 }
 private static String read(Path p){try{return Files.readString(p);}catch(Exception e){throw new IllegalStateException(e);}}
}
