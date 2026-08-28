package com.bct.ngtpa.apiservice.architecture;
import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
class AccountBalanceFundLevelPortfolioBoundaryGuardTest {
 @Test void remainsApplicationOwnedAndPresentationNeutral() throws Exception { Path root=Path.of("src/main/java/com/bct/ngtpa/apiservice/application"); String s=Files.readString(root.resolve("service/accountbalance/DefaultFundLevelPortfolioBuilder.java"))+Files.readString(root.resolve("port/in/FundLevelPortfolioBuilder.java")); assertThat(s).doesNotContain("adapter.out.apim","JsonProperty","MutationResponse","DecimalFormat","NumberFormat","factsheetUrl","@Service","Mono<"); }
}
