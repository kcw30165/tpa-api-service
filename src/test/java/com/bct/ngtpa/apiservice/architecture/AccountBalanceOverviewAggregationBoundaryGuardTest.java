package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AccountBalanceOverviewAggregationBoundaryGuardTest {
  @Test
  void overviewAggregationRemainsApplicationOwnedRawAndFocused() throws Exception {
    String dto = all(Path.of("src/main/java/com/bct/ngtpa/apiservice/application/dto/accountbalance/overview"));
    String service = Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/application/service/accountbalance/DefaultAccountOverviewAggregator.java"));
    assertThat(dto + service).doesNotContain("adapter.out.apim", "JsonProperty", "MutationResponse", "NumberFormat", "DecimalFormat", "factsheet", "fundLevelView", "subDisplayAccountLevelView", "org.springframework");
    assertThat(service).contains("BigDecimal", "displaySequence", "thenComparing", "systemFailure()");
  }
  private static String all(Path root) throws Exception { try (var paths=Files.walk(root)) { return paths.filter(Files::isRegularFile).map(AccountBalanceOverviewAggregationBoundaryGuardTest::read).reduce("",String::concat); } }
  private static String read(Path path){ try{return Files.readString(path);}catch(Exception e){throw new RuntimeException(e);} }
}
