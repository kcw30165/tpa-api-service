package com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResponseFieldTest {

  @Test
  void absentFilterUsesTheAbsentResponseFieldsHashCode() {
    var absent = ResponseField.absent();
    var filter = new ResponseField.AbsentFilter();

    assertThat(filter.equals(absent)).isTrue();
    assertThat(filter).hasSameHashCodeAs(absent).isNotEqualTo(ResponseField.present(null));
  }
}
