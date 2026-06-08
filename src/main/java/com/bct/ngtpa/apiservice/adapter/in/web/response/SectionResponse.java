package com.bct.ngtpa.apiservice.adapter.in.web.response;

import java.util.List;

public record SectionResponse(
        String id,
        String label,
        Integer displayOrder,
        List<FieldResponse> fields) {

    public SectionResponse {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
