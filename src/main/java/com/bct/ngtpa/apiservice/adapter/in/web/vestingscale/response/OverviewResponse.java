package com.bct.ngtpa.apiservice.adapter.in.web.vestingscale.response;

import java.util.List;

public record OverviewResponse(OverviewMemberResponse member, List<OverviewPlanResponse> plans) {}
