package com.bct.ngtpa.apiservice.application.dto;

import java.util.Map;

public class MemberInfoResult {

    private final Map<String, Object> payload;

    public MemberInfoResult(Map<String, Object> payload) {
        this.payload = payload;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }
}
