package com.bct.ngtpa.apiservice.adapter.in.web.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GetMessageBoardWebRequest(
        @JsonProperty("policy-no") String policyNo,
        @JsonProperty("cert-no")   String certNo,
        @JsonProperty("user-id")   String userId,
        @JsonProperty("ref-date")  String refDate,
        @JsonProperty("env")       String env,
        @JsonProperty("mbr-type")  String mbrType
) {}
