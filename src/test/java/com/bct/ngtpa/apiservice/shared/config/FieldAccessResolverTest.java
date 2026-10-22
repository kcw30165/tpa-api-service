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
  void allThreeMethodsReturnFalseForNullBlankAndUnrelatedValues() {
    String[] values = {null, "", "   ", "EDITABLE_OPTION", "SOMETHING_ELSE"};
    for (String value : values) {
      assertThat(FieldAccessResolver.isHidden(value)).isFalse();
      assertThat(FieldAccessResolver.isReadonly(value)).isFalse();
      assertThat(FieldAccessResolver.isRequired(value)).isFalse();
    }
  }
}
