package com.bct.ngtpa.apiservice.application.service.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Fail-first tests for {@link UrlValidator}. Mirrors the behaviour pinned for {@code
 * AccountBalanceFactsheetResolver}: a value is kept exactly as received when it is an absolute URI;
 * null or blank is omitted silently; anything else is omitted and reported once, without the value.
 */
class UrlValidatorTest {

  private final AtomicInteger rejected = new AtomicInteger();
  private final UrlValidator validator = new UrlValidator(rejected::incrementAndGet);

  @Test
  void keepsAbsoluteHttpAndHttpsUrlsVerbatimWithoutEvent() {
    assertThat(validator.validate("https://example.test/fund?a=1"))
        .contains("https://example.test/fund?a=1");
    assertThat(validator.validate("http://example.test/fund")).contains("http://example.test/fund");

    assertThat(rejected).hasValue(0);
  }

  @Test
  void keepsTheValueExactlyAsReceived() {
    assertThat(validator.validate("HTTPS://EXAMPLE.TEST/Fund%20Sheet.pdf?x=A&y=B#p2"))
        .contains("HTTPS://EXAMPLE.TEST/Fund%20Sheet.pdf?x=A&y=B#p2");
  }

  @Test
  void keepsNonAsciiPathVerbatim() {
    assertThat(validator.validate("https://example.test/\u57fa\u91d1.pdf"))
        .contains("https://example.test/\u57fa\u91d1.pdf");

    assertThat(rejected).hasValue(0);
  }

  @Test
  void nullEmptyAndBlankAreOmittedWithoutEvent() {
    assertThat(validator.validate(null)).isEmpty();
    assertThat(validator.validate("")).isEmpty();
    assertThat(validator.validate("   ")).isEmpty();

    assertThat(rejected).hasValue(0);
  }

  @Test
  void relativeReferencesAreOmittedAndReportedOnce() {
    assertThat(validator.validate("/relative/path")).isEmpty();
    assertThat(rejected).hasValue(1);

    assertThat(validator.validate("example.test/fund")).isEmpty();
    assertThat(rejected).hasValue(2);
  }

  @Test
  void malformedValuesAreOmittedAndReported() {
    assertThat(validator.validate("not a url with secret=abc")).isEmpty();
    assertThat(validator.validate("https://example.test/a b")).isEmpty();

    assertThat(rejected).hasValue(2);
  }

  @Test
  void valuesAreNotTrimmed() {
    assertThat(validator.validate(" https://example.test/fund")).isEmpty();
    assertThat(validator.validate("https://example.test/fund ")).isEmpty();

    assertThat(rejected).hasValue(2);
  }

  @Test
  void schemeAndHostAreNotRestricted() {
    assertThat(validator.validate("javascript:alert(1)")).contains("javascript:alert(1)");
    assertThat(validator.validate("data:text/plain,hello")).contains("data:text/plain,hello");
    assertThat(validator.validate("mailto:someone@example.test"))
        .contains("mailto:someone@example.test");

    assertThat(rejected).hasValue(0);
  }

  @Test
  void eachRejectedCallReportsExactlyOnceAndAcceptedCallsDoNot() {
    validator.validate("/x");
    validator.validate("https://ok.test/");
    validator.validate("/y");

    assertThat(rejected).hasValue(2);
  }

  @Test
  void aRejectedValueDoesNotAffectTheNextValidValue() {
    assertThat(validator.validate("/x")).isEmpty();

    assertThat(validator.validate("https://ok.test/")).contains("https://ok.test/");
  }

  @Test
  void callbackIsRequired() {
    assertThatThrownBy(() -> new UrlValidator(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("onRejected");
  }
}
