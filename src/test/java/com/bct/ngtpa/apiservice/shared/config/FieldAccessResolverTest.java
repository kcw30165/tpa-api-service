package com.bct.ngtpa.apiservice.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FieldAccessResolverTest {

  @Test
  void isHiddenIsCaseInsensitiveAndMatchesOnlyHidden() {
    assertThat(FieldAccessResolver.isHidden("HIDDEN")).isTrue();
    assertThat(FieldAccessResolver.isHidden("hidden")).isTrue();
    assertThat(FieldAccessResolver.isHidden("Hidden")).isTrue();
    assertThat(FieldAccessResolver.isHidden("READONLY")).isFalse();
    assertThat(FieldAccessResolver.isHidden("EDITABLE_COM")).isFalse();
    assertThat(FieldAccessResolver.isHidden("EDITABLE_OPTION")).isFalse();
  }

  @Test
  void isReadonlyIsCaseInsensitiveAndMatchesOnlyReadonly() {
    assertThat(FieldAccessResolver.isReadonly("READONLY")).isTrue();
    assertThat(FieldAccessResolver.isReadonly("readonly")).isTrue();
    assertThat(FieldAccessResolver.isReadonly("ReadOnly")).isTrue();
    assertThat(FieldAccessResolver.isReadonly("HIDDEN")).isFalse();
    assertThat(FieldAccessResolver.isReadonly("EDITABLE_COM")).isFalse();
    assertThat(FieldAccessResolver.isReadonly("EDITABLE_OPTION")).isFalse();
  }

  @Test
  void isRequiredIsCaseInsensitiveAndMatchesOnlyEditableCom() {
    assertThat(FieldAccessResolver.isRequired("EDITABLE_COM")).isTrue();
    assertThat(FieldAccessResolver.isRequired("editable_com")).isTrue();
    assertThat(FieldAccessResolver.isRequired("Editable_Com")).isTrue();
    assertThat(FieldAccessResolver.isRequired("EDITABLE_OPTION")).isFalse();
    assertThat(FieldAccessResolver.isRequired("READONLY")).isFalse();
    assertThat(FieldAccessResolver.isRequired("HIDDEN")).isFalse();
  }

  @Test
  void editableOptionIsNotHiddenReadonlyOrRequired() {
    assertThat(FieldAccessResolver.isHidden("EDITABLE_OPTION")).isFalse();
    assertThat(FieldAccessResolver.isReadonly("EDITABLE_OPTION")).isFalse();
    assertThat(FieldAccessResolver.isRequired("EDITABLE_OPTION")).isFalse();
  }

  @Test
  void nullAndBlankValuesAreBlankAndTreatedAsHiddenNotReadonlyNotRequired() {
    String[] values = {null, "", "   "};
    for (String value : values) {
      assertThat(FieldAccessResolver.isBlank(value)).isTrue();
      assertThat(FieldAccessResolver.isHidden(value)).isTrue();
      assertThat(FieldAccessResolver.isReadonly(value)).isFalse();
      assertThat(FieldAccessResolver.isRequired(value)).isFalse();
      assertThat(FieldAccessResolver.isRequired(value, Boolean.TRUE)).isFalse();
    }
  }

  @Test
  void nonBlankValuesAreNeverBlank() {
    String[] values = {"HIDDEN", "READONLY", "EDITABLE_COM", "EDITABLE_OPTION", "SOMETHING_ELSE"};
    for (String value : values) {
      assertThat(FieldAccessResolver.isBlank(value)).isFalse();
    }
  }

  @Test
  void unrecognisedValueIsTreatedAsHiddenNotReadonlyNotRequired() {
    assertThat(FieldAccessResolver.isUnrecognised("SOMETHING_ELSE")).isTrue();
    assertThat(FieldAccessResolver.isHidden("SOMETHING_ELSE")).isTrue();
    assertThat(FieldAccessResolver.isReadonly("SOMETHING_ELSE")).isFalse();
    assertThat(FieldAccessResolver.isRequired("SOMETHING_ELSE")).isFalse();
    assertThat(FieldAccessResolver.isHidden("EDITABLE")).isTrue();
  }

  @Test
  void knownNullAndBlankValuesAreNotUnrecognised() {
    String[] values = {
      "HIDDEN", "READONLY", "EDITABLE_COM", "EDITABLE_OPTION", "editable_option", null, "", "   "
    };
    for (String value : values) {
      assertThat(FieldAccessResolver.isUnrecognised(value)).isFalse();
    }
  }

  @Test
  void requiredOverrideFalseLowersRequirementOnly() {
    assertThat(FieldAccessResolver.isRequired("EDITABLE_COM", Boolean.FALSE)).isFalse();
    assertThat(FieldAccessResolver.isRequired("EDITABLE_COM", null)).isTrue();
    assertThat(FieldAccessResolver.isRequired("EDITABLE_COM", Boolean.TRUE)).isTrue();
    assertThat(FieldAccessResolver.isRequired("EDITABLE_OPTION", Boolean.TRUE)).isFalse();
    assertThat(FieldAccessResolver.isRequired("READONLY", Boolean.TRUE)).isFalse();
  }

  @Test
  void surroundingWhitespaceDoesNotChangeClassification() {
    assertThat(FieldAccessResolver.isRequired(" EDITABLE_COM ")).isTrue();
    assertThat(FieldAccessResolver.isHidden(" HIDDEN ")).isTrue();
    assertThat(FieldAccessResolver.isReadonly(" READONLY ")).isTrue();
    assertThat(FieldAccessResolver.isUnrecognised(" EDITABLE_OPTION ")).isFalse();
    assertThat(FieldAccessResolver.isHidden(" EDITABLE_OPTION ")).isFalse();
  }
}
