package com.bct.ngtpa.apiservice.architecture;
import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
class AccountBalanceCurrencyVisibilityBoundaryGuardTest {
 @Test void omissionIsScopedAndForbiddenFieldIsAbsent() throws Exception {String portfolio=Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/response/accountbalance/InvestmentPortfolioItemResponse.java"));String overview=Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/response/accountbalance/AccountOverviewResponse.java"));String all=portfolio+overview+Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/application/service/accountbalance/AccountBalanceFactsheetResolver.java"));assertThat(portfolio).contains("Include.NON_NULL");assertThat(overview).contains("Include.ALWAYS");assertThat(all).doesNotContain("fundFactsheetEnabled","setSerializationInclusion");}
 @Test void malformedLoggerCannotReceiveTheUrl() throws Exception {String port=Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/application/port/out/AccountBalancePresentationEventPort.java"));String logger=Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/infrastructure/logging/AccountBalancePresentationEventLogger.java"));assertThat(port).contains("void malformedFactsheet()").doesNotContain("String");assertThat(logger).doesNotContain("url","factsheetUrl");}
}
