package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCertificateHelper;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.config.ApimProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.cert.CertificateEncodingException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class ApimAppCertificateService {

    private final ApimProperties apimProperties;
    private final ApimCertificateHelper apimCertificateHelper;

    private PrivateKey appPrivateKey;
    private PublicKey appPublicKey;
    private String certificateHeaderValue;

    @PostConstruct
    void init() {
        if (!apimProperties.getEncryption().isEnabled()) {
            return;
        }
        String privateKeyPem = apimProperties.getEncryption().getPrivateKeyPem();
        String publicKeyPem = apimProperties.getEncryption().getPublicKeyPem();
        if (!StringUtils.hasText(privateKeyPem) || !StringUtils.hasText(publicKeyPem)) {
            return;
        }
        try {
            this.appPublicKey = apimCertificateHelper.getCustomerPublicKey(publicKeyPem, "RSA");
            this.appPrivateKey = apimCertificateHelper.getCustomerPrivateKey(privateKeyPem);
            X509Certificate certificate = apimCertificateHelper.generateCertificate(appPublicKey, appPrivateKey);
            this.certificateHeaderValue = Base64.getEncoder().encodeToString(certificate.getEncoded());
        } catch (CertificateEncodingException ex) {
            throw new ApimCryptoException("Unable to initialize APIM application certificate.", ex);
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
