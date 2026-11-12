package com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record VestingScaleDataResponse(
    List<PlanTypeOptionResponse> planTypes,
    OverviewResponse overview,
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = ResponseField.AbsentFilter.class)
        ResponseField<VestingScaleSectionResponse> vestingScale) {}
