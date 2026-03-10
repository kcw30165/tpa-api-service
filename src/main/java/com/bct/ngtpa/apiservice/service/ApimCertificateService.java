package com.bct.ngtpa.apiservice.service;

import com.bct.ngtpa.apiservice.config.ApimProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
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

    public Mono<String> getBctPublicKeyMaterial() {
        String apiKey = apimProperties.getEncryption().getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            return fallbackPublicKeyMaterial();
        }

        return apimWebClient.get()
                .uri(uriBuilder -> uriBuilder.path(apimProperties.getEncryption().getCertificatePath()).build())
                .header("KeyId", apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .switchIfEmpty(fallbackPublicKeyMaterial());
    }

    private Mono<String> fallbackPublicKeyMaterial() {
        String configuredPublicKey = apimProperties.getEncryption().getPublicKeyPem();
        if (StringUtils.hasText(configuredPublicKey)) {
            return Mono.just(configuredPublicKey);
        }
        return Mono.error(new IllegalStateException(
                "Configure apim.encryption.apiKey for dynamic certificate lookup or apim.encryption.publicKeyPem as a fallback."));
    }
}
