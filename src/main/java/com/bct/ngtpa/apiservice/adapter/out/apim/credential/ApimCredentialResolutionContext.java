package com.bct.ngtpa.apiservice.adapter.out.apim.credential;

import java.util.Map;

public record ApimCredentialResolutionContext(
        String trustId,
        String schemeId,
        String policyNo,
        String memberId,
        String userId,
        String env,
        String mbrType,
        String operationName,
        Map<String, String> requestMetadata
) {}
