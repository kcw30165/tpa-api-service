package com.bct.ngtpa.apiservice.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FieldGroupAccessNormalizerTest {

  private static final List<String> GROUP = List.of("itemA", "itemB");

  @Test
  void groupWithAllReadonlyFieldsStaysReadonlyAndNotRequired() {
    Map<String, Boolean> readonly = Map.of("itemA", true, "itemB", true);
    Map<String, Boolean> required = Map.of("itemA", false, "itemB", false);

    FieldGroupAccessNormalizer.Result result =
        FieldGroupAccessNormalizer.normalize("residentialAddress", GROUP, readonly, required);

    assertThat(result.readonlyByItemId()).containsEntry("itemA", true).containsEntry("itemB", true);
    assertThat(result.requiredByItemId())
        .containsEntry("itemA", false)
        .containsEntry("itemB", false);
    assertThat(result.inconsistent()).isFalse();
  }

  @Test
  void anyNonReadonlyFieldMakesWholeGroupNonReadonly() {
    Map<String, Boolean> readonly = Map.of("itemA", true, "itemB", false);
    Map<String, Boolean> required = Map.of("itemA", false, "itemB", false);

    FieldGroupAccessNormalizer.Result result =
        FieldGroupAccessNormalizer.normalize("residentialAddress", GROUP, readonly, required);

    assertThat(result.readonlyByItemId())
        .containsEntry("itemA", false)
        .containsEntry("itemB", false);
    assertThat(result.inconsistent()).isTrue();
  }

  @Test
  void anyRequiredFieldMakesWholeGroupRequired() {
    Map<String, Boolean> readonly = Map.of("itemA", false, "itemB", false);
    Map<String, Boolean> required = Map.of("itemA", true, "itemB", false);

    FieldGroupAccessNormalizer.Result result =
        FieldGroupAccessNormalizer.normalize("residentialAddress", GROUP, readonly, required);

    assertThat(result.requiredByItemId()).containsEntry("itemA", true).containsEntry("itemB", true);
    assertThat(result.inconsistent()).isTrue();
  }

  @Test
  void bothReadonlyAndRequiredDisagreementsAreDetectedTogether() {
    Map<String, Boolean> readonly = Map.of("itemA", true, "itemB", false);
    Map<String, Boolean> required = Map.of("itemA", true, "itemB", false);

    FieldGroupAccessNormalizer.Result result =
        FieldGroupAccessNormalizer.normalize("residentialAddress", GROUP, readonly, required);

    assertThat(result.readonlyByItemId())
        .containsEntry("itemA", false)
        .containsEntry("itemB", false);
    assertThat(result.requiredByItemId()).containsEntry("itemA", true).containsEntry("itemB", true);
    assertThat(result.inconsistent()).isTrue();
  }

  @Test
  void fieldsAbsentFromTheGivenMapsAreTreatedAsNotReadonlyAndNotRequired() {
    // Item ids present in the group but absent from fieldStates (e.g. HIDDEN
    // and filtered out upstream, or missing YAML binding) are simply not
    // considered -- the normalizer only acts on item ids present in both maps
    // and the group list.
    Map<String, Boolean> readonly = Map.of("itemA", true);
    Map<String, Boolean> required = Map.of("itemA", false);

    FieldGroupAccessNormalizer.Result result =
        FieldGroupAccessNormalizer.normalize("residentialAddress", GROUP, readonly, required);

    assertThat(result.readonlyByItemId()).containsEntry("itemA", true);
    assertThat(result.readonlyByItemId()).doesNotContainKey("itemB");
    assertThat(result.inconsistent()).isFalse();
  }

  @Test
  void emptyGroupProducesEmptyResultAndIsNotInconsistent() {
    FieldGroupAccessNormalizer.Result result =
        FieldGroupAccessNormalizer.normalize("emptyGroup", List.of(), Map.of(), Map.of());

    assertThat(result.readonlyByItemId()).isEmpty();
    assertThat(result.requiredByItemId()).isEmpty();
    assertThat(result.inconsistent()).isFalse();
  }

  @Test
  void singleFieldGroupIsNeverInconsistent() {
    Map<String, Boolean> readonly = Map.of("itemA", false);
    Map<String, Boolean> required = Map.of("itemA", true);

    FieldGroupAccessNormalizer.Result result =
        FieldGroupAccessNormalizer.normalize("businessPhone", List.of("itemA"), readonly, required);

    assertThat(result.inconsistent()).isFalse();
    assertThat(result.readonlyByItemId()).containsEntry("itemA", false);
    assertThat(result.requiredByItemId()).containsEntry("itemA", true);
  }
}
