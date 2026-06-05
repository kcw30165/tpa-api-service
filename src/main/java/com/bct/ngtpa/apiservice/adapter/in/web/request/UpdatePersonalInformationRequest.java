package com.bct.ngtpa.apiservice.adapter.in.web.request;

import java.util.Map;

public record UpdatePersonalInformationRequest(
        String formVersion,
        Boolean applyToAllAccounts,
        Map<String, Object> fields) {
}
