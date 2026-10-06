package com.bct.ngtpa.apiservice.application.usecase.fundprice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceModeSessionMetadata;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Dedicated coverage-closure tests for FundPriceModeUseCaseResult's compact constructor.
 * Confirmed (script 154's investigation): the class's 2 factory methods (applied/systemFailure)
 * are both already fully covered via FundPriceModeUseCaseTest, but the compact constructor's 3
 * validation guard clauses are NEVER exercised by real production code -- both real call sites
 * always construct valid combinations by design. This test constructs the record DIRECTLY,
 * bypassing the factories, to exercise each guard clause explicitly.
 */
class FundPriceModeUseCaseResultTest {

  private FundPriceModeSessionMetadata validMetadata() {
    return new FundPriceModeSessionMetadata(
        "DAILY", "BIDOFFER PRICE", List.of(LocalDate.of(2026, 5, 5)), 36, "5");
  }

  @Test
  void nullOutcomeThrows() {
    assertThatThrownBy(() -> new FundPriceModeUseCaseResult(null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("outcome must not be null");
  }

  @Test
  void appliedWithoutMetadataThrows() {
    assertThatThrownBy(
            () -> new FundPriceModeUseCaseResult(FundPriceModeUseCaseOutcome.APPLIED, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("APPLIED result must carry metadata");
  }

  @Test
  void nonAppliedWithMetadataThrows() {
    assertThatThrownBy(
            () ->
                new FundPriceModeUseCaseResult(
                    FundPriceModeUseCaseOutcome.SYSTEM_FAILURE, validMetadata()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("non-APPLIED result must not carry metadata");
  }

  @Test
  void appliedWithMetadataConstructsSuccessfully() {
    var metadata = validMetadata();
    var result = new FundPriceModeUseCaseResult(FundPriceModeUseCaseOutcome.APPLIED, metadata);
    assertThat(result.outcome()).isEqualTo(FundPriceModeUseCaseOutcome.APPLIED);
    assertThat(result.metadata()).isEqualTo(metadata);
  }

  @Test
  void systemFailureWithoutMetadataConstructsSuccessfully() {
    var result =
        new FundPriceModeUseCaseResult(FundPriceModeUseCaseOutcome.SYSTEM_FAILURE, null);
    assertThat(result.outcome()).isEqualTo(FundPriceModeUseCaseOutcome.SYSTEM_FAILURE);
    assertThat(result.metadata()).isNull();
  }
}
