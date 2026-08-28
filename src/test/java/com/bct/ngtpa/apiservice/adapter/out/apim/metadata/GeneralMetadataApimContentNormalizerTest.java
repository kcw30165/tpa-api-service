package com.bct.ngtpa.apiservice.adapter.out.apim.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.apim.metadata.dto.GeneralMetadataApimRow;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataResolutionSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeneralMetadataApimContentNormalizerTest {
  private final GeneralMetadataApimContentNormalizer normalizer =
      new GeneralMetadataApimContentNormalizer();

  @Test
  void acceptsDefaultAndPolicySpecificRows() {
    assertThat(
            normalizer
                .normalize("00000008802", List.of(row("all", "fund-pri", "precision", "6")))
                .resolutionSource())
        .isEqualTo(GeneralMetadataResolutionSource.DEFAULT_ALL);
    assertThat(
            normalizer
                .normalize("00000008802", List.of(row("00000008802", "nav", "precision", "2")))
                .resolutionSource())
        .isEqualTo(GeneralMetadataResolutionSource.POLICY_SPECIFIC);
  }

  @Test
  void permitsEmptyDataForTargetedFallback() {
    assertThat(normalizer.normalize("00000008802", List.of()).entries()).isEmpty();
  }

  @Test
  void deduplicatesExactRowsAndRejectsConflicts() {
    var exact = row("all", "fund-pri", "precision", "6");
    assertThat(normalizer.normalize("00000008802", List.of(exact, exact)).entries()).hasSize(1);
    assertThatThrownBy(
            () ->
                normalizer.normalize(
                    "00000008802", List.of(exact, row("all", "fund-pri", "precision", "7"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Conflicting duplicate general metadata row");
  }

  @Test
  void rejectsForeignPolicyUnknownWireValuesAndMalformedPrecision() {
    assertThatThrownBy(
            () ->
                normalizer.normalize(
                    "00000008802", List.of(row("999", "fund-pri", "precision", "6"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unexpected general metadata policy");
    assertThatThrownBy(
            () ->
                normalizer.normalize(
                    "00000008802", List.of(row("all", "unknown", "precision", "6"))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                normalizer.normalize(
                    "00000008802", List.of(row("all", "fund-pri", "unknown", "6"))))
        .isInstanceOf(IllegalArgumentException.class);
    for (String value : List.of("", " ", "-1", "1.5", "abc")) {
      assertThatThrownBy(
              () ->
                  normalizer.normalize(
                      "00000008802", List.of(row("all", "fund-pri", "precision", value))))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Invalid general metadata precision");
    }
  }

  private static GeneralMetadataApimRow row(String policy, String name, String type, String value) {
    return new GeneralMetadataApimRow(policy, name, type, value);
  }

  @Test
  void phaseIi002V002RepresentsEmptyAsUnresolvedAndRejectsMalformedRows() {
    assertThat(normalizer.normalize("00000008802", List.of()).resolutionSource()).isEmpty();
    assertThatThrownBy(
            () ->
                normalizer.normalize(
                    "00000008802",
                    List.of(
                        row("all", "fund-pri", "precision", "6"),
                        row("00000008802", "nav", "precision", "2"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Mixed general metadata policy resolution");
    assertThatThrownBy(
            () ->
                normalizer.normalize(
                    "00000008802", java.util.Arrays.asList((GeneralMetadataApimRow) null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("General metadata row must not be null");
    for (GeneralMetadataApimRow malformed :
        List.of(
            row("", "fund-pri", "precision", "6"),
            row("all", "", "precision", "6"),
            row("all", "fund-pri", "", "6"),
            row("all", "fund-pri", "round-method", ""))) {
      assertThatThrownBy(() -> normalizer.normalize("00000008802", List.of(malformed)))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
