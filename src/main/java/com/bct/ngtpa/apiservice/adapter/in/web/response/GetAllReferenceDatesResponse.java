package com.bct.ngtpa.apiservice.adapter.in.web.response;

import java.util.List;

public record GetAllReferenceDatesResponse(List<ReferenceDateEntryResponse> referenceDates) {
    public GetAllReferenceDatesResponse {
        referenceDates = referenceDates == null ? List.of() : List.copyOf(referenceDates);
    }
}
