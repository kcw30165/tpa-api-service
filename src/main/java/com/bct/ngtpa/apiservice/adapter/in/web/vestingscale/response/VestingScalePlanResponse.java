package com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record VestingScalePlanResponse(
    String planNo,
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = ResponseField.AbsentFilter.class)
        ResponseField<List<VestingScaleRowResponse>> scales) {}
