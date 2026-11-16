package com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response;

import com.fasterxml.jackson.annotation.JsonInclude;

public record OverviewMemberResponse(
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = ResponseField.AbsentFilter.class)
        ResponseField<String> memberCategory,
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = ResponseField.AbsentFilter.class)
        ResponseField<String> dateJoinedCompany,
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = ResponseField.AbsentFilter.class)
        ResponseField<String> dateJoinedScheme,
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = ResponseField.AbsentFilter.class)
        ResponseField<String> employmentDate) {

  public OverviewMemberResponse() {
    this(
        ResponseField.absent(),
        ResponseField.absent(),
        ResponseField.absent(),
        ResponseField.absent());
  }
}
