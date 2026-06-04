package com.bct.ngtpa.apiservice.adapter.in.web.request;

import java.util.Map;

public record UpdatePersonalInformationRequest(
        String formVersion,
        Map<String, Object> fields) {
}
