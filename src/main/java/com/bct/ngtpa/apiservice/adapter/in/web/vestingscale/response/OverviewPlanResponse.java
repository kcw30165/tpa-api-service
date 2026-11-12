package com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response;

import com.fasterxml.jackson.annotation.JsonInclude;

public record OverviewPlanResponse(
    String planNo,
    String planType,
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = ResponseField.AbsentFilter.class)
        ResponseField<String> planEffectiveDate,
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = ResponseField.AbsentFilter.class)
        ResponseField<String> dateOfJoiningPlan,
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = ResponseField.AbsentFilter.class)
        ResponseField<PlanServiceResponse> planService,
    String vestingPeriodBasedOn,
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = ResponseField.AbsentFilter.class)
        ResponseField<String> memberContributionBasis,
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = ResponseField.AbsentFilter.class)
        ResponseField<String> employerContributionBasis) {}
