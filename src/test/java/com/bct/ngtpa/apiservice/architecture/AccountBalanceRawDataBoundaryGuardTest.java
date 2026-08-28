package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AccountBalanceRawDataBoundaryGuardTest {
  @Test
  void applicationOwnedRawBoundaryIsTransportAndPresentationNeutral() throws Exception {
    Path root = Path.of("src/main/java/com/bct/ngtpa/apiservice/application/dto/accountbalance/raw");
    String all = Files.walk(root).filter(Files::isRegularFile).map(p -> { try { return Files.readString(p); } catch (Exception e) { throw new RuntimeException(e); } }).reduce("", String::concat);
    String port = Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/application/port/out/AccountBalanceSourcePort.java"));
    assertThat(all).doesNotContain("com.fasterxml.jackson", "JsonProperty", "adapter.out.apim", "MutationResponse", "factsheetUrl", "NumberFormat");
    assertThat(port).contains("Mono<RawAccountBalanceData>").doesNotContain("TrpGetMemberAccBal", "AccountBalanceResult", "adapter.out.apim");
  }
}
