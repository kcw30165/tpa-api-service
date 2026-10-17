package com.bct.ngtpa.apiservice.application.dto.fundcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData.RawFundComparisonOption;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Closes the confirmed jacoco gaps on RawFundComparisonOptionsData's and the nested
 * RawFundComparisonOption's compact constructors (script 049 evidence, lines 23-24, 37, 40, 43): no
 * existing test file constructs either record directly with the invalid inputs their own guards
 * defend against -- every real caller, the adapter, only ever supplies
 * already-structurally-validated data, so these throw-paths (and the null-chinFundName coalescing
 * path, as opposed to the already-tested empty-string path) were never exercised.
 */
class RawFundComparisonOptionsDataTest {

  private static RawFundComparisonOption validOption() {
    return new RawFundComparisonOption("FAEU", "Fidelity Advantage Portfolio Fund", "");
  }

  // ----- RawFundComparisonOptionsData's own guards --------------------------------
  @Test
  void rejectsNullRawTimeRange() {
    var funds = List.of(validOption());
    assertThatThrownBy(() -> new RawFundComparisonOptionsData(null, funds))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rawTimeRange");
  }

  @Test
  void rejectsBlankRawTimeRange() {
    var funds = List.of(validOption());
    assertThatThrownBy(() -> new RawFundComparisonOptionsData("   ", funds))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rawTimeRange");
  }

  @Test
  void rejectsNullFunds() {
    assertThatThrownBy(() -> new RawFundComparisonOptionsData("10D", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("funds");
  }

  @Test
  void rejectsEmptyFunds() {
    var emptyFunds = List.<RawFundComparisonOption>of();
    assertThatThrownBy(() -> new RawFundComparisonOptionsData("10D", emptyFunds))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("funds");
  }

  @Test
  void acceptsValidArguments() {
    var data = new RawFundComparisonOptionsData("10D", List.of(validOption()));
    assertThat(data.rawTimeRange()).isEqualTo("10D");
    assertThat(data.funds()).containsExactly(validOption());
  }

  // ----- nested RawFundComparisonOption's own guards -------------------------------
  @Test
  void rejectsNullFundCode() {
    assertThatThrownBy(() -> new RawFundComparisonOption(null, "Name", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fundCode");
  }

  @Test
  void rejectsBlankFundCode() {
    assertThatThrownBy(() -> new RawFundComparisonOption("   ", "Name", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fundCode");
  }

  @Test
  void rejectsNullFundName() {
    assertThatThrownBy(() -> new RawFundComparisonOption("FAEU", null, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fundName");
  }

  @Test
  void rejectsBlankFundName() {
    assertThatThrownBy(() -> new RawFundComparisonOption("FAEU", "   ", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fundName");
  }

  @Test
  void nullChinFundNameIsCoalescedToAnEmptyString() {
    var option = new RawFundComparisonOption("FAEU", "Name", null);
    assertThat(option.chinFundName()).isEmpty();
  }

  @Test
  void emptyStringChinFundNameIsPreservedAsIs() {
    var option = new RawFundComparisonOption("FAEU", "Name", "");
    assertThat(option.chinFundName()).isEmpty();
  }

  @Test
  void nonBlankChinFundNameIsPreservedVerbatim() {
    var option = new RawFundComparisonOption("FAEU", "Name", "\u4e2d\u6587\u540d\u7a31");
    assertThat(option.chinFundName()).isEqualTo("\u4e2d\u6587\u540d\u7a31");
  }
}
