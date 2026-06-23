package com.bct.ngtpa.apiservice.application.dto;

import java.util.List;

public record GetAllReferenceDatesResult(List<ReferenceDateEntryResult> referenceDates) {
    public GetAllReferenceDatesResult {
        referenceDates = referenceDates == null ? List.of() : List.copyOf(referenceDates);
    }
}
