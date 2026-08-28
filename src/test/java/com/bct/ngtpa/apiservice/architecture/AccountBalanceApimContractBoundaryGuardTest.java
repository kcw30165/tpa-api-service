package com.bct.ngtpa.apiservice.architecture;
import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
class AccountBalanceApimContractBoundaryGuardTest {
 @Test void contractContainsNoExecutorAdapterOrPresentationLogic() throws Exception {
  Path root=Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/accountbalance");
  String all=Files.walk(root).filter(Files::isRegularFile).map(p->{try{return Files.readString(p);}catch(Exception e){throw new RuntimeException(e);}}).reduce("",String::concat);
  assertThat(all).doesNotContain("ApimExchangeExecutor", "MutationResponse", "NumberFormat", "factsheetUrl", "@Component", "@Service");
 }
}
