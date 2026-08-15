package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ForgotPasswordCompletionCompatibilitySeamTest {
 @Test void testTransportIsCompatibilityOnlyAndProductionConstructorUsesExecutor() throws Exception {
  String source=Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/forgotpassword/ApimForgotPasswordCompletionAdapter.java"));
  assertThat(source).contains("@Autowired public ApimForgotPasswordCompletionAdapter(ApimExchangeExecutor executor)",
    "public ApimForgotPasswordCompletionAdapter(ApimForgotPasswordCompletionTransport transport)",
    "if(testTransport!=null)");
  assertThat(source).doesNotContain("@Autowired public ApimForgotPasswordCompletionAdapter(ApimForgotPasswordCompletionTransport");
 }
}
