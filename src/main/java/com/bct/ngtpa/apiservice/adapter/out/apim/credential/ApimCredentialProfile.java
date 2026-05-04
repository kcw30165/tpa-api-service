package com.bct.ngtpa.apiservice.adapter.out.apim.credential;

import java.util.List;

public record ApimCredentialProfile(
        String profileId,
        String clientId,
        String clientSecret,
        String apiKey,
        String tokenUri,
        String baseUrl,
        List<String> scope,
        String certificatePath
) {}
