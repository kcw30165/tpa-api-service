package com.bct.ngtpa.apiservice.application.service.accountbalance;
import static org.assertj.core.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
class AccountBalanceFactsheetResolverTest {
 @Test void retainsValidAbsoluteAndOmitsEmpty(){var r=new AccountBalanceFactsheetResolver(()->{});assertThat(r.resolve("https://example.test/fund?a=1")).contains("https://example.test/fund?a=1");assertThat(r.resolve("http://example.test/fund")).contains("http://example.test/fund");assertThat(r.resolve(null)).isEmpty();assertThat(r.resolve("  ")).isEmpty();}
 @Test void malformedOrRelativeIsSafelyOmittedAndReportedWithoutValue(){var calls=new AtomicInteger();var r=new AccountBalanceFactsheetResolver(calls::incrementAndGet);assertThat(r.resolve("/relative/path")).isEmpty();assertThat(r.resolve("not a url with secret=abc")).isEmpty();assertThat(calls).hasValue(2);}
}
