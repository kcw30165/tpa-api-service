package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCertificateHelper;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.config.ApimProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.security.PublicKey;

@Service
@RequiredArgsConstructor
public class ApimCertificateService {

    private final WebClient apimWebClient;
    private final ApimProperties apimProperties;
    private final ApimCertificateHelper apimCertificateHelper;

    public Mono<PublicKey> getBctPublicKey() {
        String apiKey = apimProperties.getEncryption().getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            return Mono.error(new ApimCryptoException("apim.encryption.apiKey is required."));
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
                .switchIfEmpty(Mono.error(
                        new ApimCryptoException("BCT certificate API returned an empty response.")))
                .map(apimCertificateHelper::getPublicKeyFromCertificate);
    }
}
