package com.bct.ngtpa.apiservice.service;

import com.bct.ngtpa.apiservice.config.ApimProperties;
import com.bct.ngtpa.apiservice.util.apim.ApimCertUtility;
import java.security.PublicKey;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class ApimCertificateService {
    private final WebClient apimWebClient;
    private final ApimProperties apimProperties;

    public ApimCertificateService(WebClient apimWebClient, ApimProperties apimProperties) {
        this.apimWebClient = apimWebClient;
        this.apimProperties = apimProperties;
    }

    public Mono<PublicKey> getBctPublicKey() {
        String apiKey = apimProperties.getEncryption().getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            return Mono.error(new IllegalStateException("apim.encryption.apiKey is required."));
        }

        return apimWebClient.get()
                .uri(UriComponentsBuilder.fromUriString(apimProperties.getBaseUrl())
                        .replacePath(apimProperties.getEncryption().getCertificatePath())
                        .replaceQuery(null)
                        .build(true)
                        .toUri())
                .header("KeyId", apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .switchIfEmpty(Mono.error(new IllegalStateException("BCT certificate API returned an empty response.")))
                .map(certificate -> {
                    try {
                        return ApimCertUtility.getPublicKeyFromCert(certificate);
                    } catch (Exception ex) {
                        throw new IllegalStateException("Failed to parse BCT public certificate.", ex);
                    }
                });
    }
}
