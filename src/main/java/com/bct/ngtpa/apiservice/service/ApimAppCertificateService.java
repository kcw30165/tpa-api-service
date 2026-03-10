package com.bct.ngtpa.apiservice.service;

import com.bct.ngtpa.apiservice.config.ApimProperties;
import com.bct.ngtpa.apiservice.util.apim.ApimCertUtility;
import jakarta.annotation.PostConstruct;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ApimAppCertificateService {
    private final ApimProperties apimProperties;

    private PrivateKey appPrivateKey;
    private PublicKey appPublicKey;
    private String certificateHeaderValue;

    public ApimAppCertificateService(ApimProperties apimProperties) {
        this.apimProperties = apimProperties;
    }

    @PostConstruct
    void init() {
        try {
            String privateKeyPem = apimProperties.getEncryption().getPrivateKeyPem();
            String publicKeyPem = apimProperties.getEncryption().getPublicKeyPem();
            if (!StringUtils.hasText(privateKeyPem) || !StringUtils.hasText(publicKeyPem)) {
                throw new IllegalStateException("apim.encryption.privateKeyPem and publicKeyPem are required.");
            }

            this.appPublicKey = ApimCertUtility.getCustomerPublicKey(publicKeyPem, "RSA");
            this.appPrivateKey = ApimCertUtility.getCustomerPrivateKey(privateKeyPem);
            X509Certificate certificate = ApimCertUtility.generateCert(appPublicKey, appPrivateKey);
            this.certificateHeaderValue = Base64.getEncoder().encodeToString(certificate.getEncoded());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to initialize APIM application certificate.", ex);
        }
    }

    public PrivateKey getAppPrivateKey() {
        return appPrivateKey;
    }

    public PublicKey getAppPublicKey() {
        return appPublicKey;
    }

    public String getCertificateHeaderValue() {
        return certificateHeaderValue;
    }
}
