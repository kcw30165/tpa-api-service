package com.bct.ngtpa.apiservice.service;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.stereotype.Service;
import com.bct.ngtpa.apiservice.util.apim.RsaFieldCryptoUtil;

@Service
public class ApimAppCertificateService {
    private static final String BC_PROVIDER = "BC";
    private static final X500Name CERT_SUBJECT = new X500Name("CN=Application");

    private final RsaFieldCryptoUtil rsaFieldCryptoUtil;
    private volatile String certificateHeaderValue;

    public ApimAppCertificateService(RsaFieldCryptoUtil rsaFieldCryptoUtil) {
        this.rsaFieldCryptoUtil = rsaFieldCryptoUtil;
        Security.addProvider(new BouncyCastleProvider());
    }

    public String getCertificateHeaderValue() {
        if (certificateHeaderValue == null) {
            synchronized (this) {
                if (certificateHeaderValue == null) {
                    certificateHeaderValue = createCertificateHeaderValue();
                }
            }
        }
        return certificateHeaderValue;
    }

    private String createCertificateHeaderValue() {
        try {
            PrivateKey privateKey = rsaFieldCryptoUtil.getConfiguredPrivateKey();
            PublicKey publicKey = derivePublicKey(privateKey);
            X509Certificate certificate = generateCertificate(publicKey, privateKey);
            return Base64.getEncoder().encodeToString(certificate.getEncoded());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to generate APIM application certificate.", ex);
        }
    }

    private PublicKey derivePublicKey(PrivateKey privateKey) throws Exception {
        if (!(privateKey instanceof RSAPrivateCrtKey rsaPrivateKey)) {
            throw new IllegalStateException("APIM private key must be an RSA CRT key.");
        }

        RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(
                rsaPrivateKey.getModulus(),
                rsaPrivateKey.getPublicExponent());
        return KeyFactory.getInstance("RSA").generatePublic(publicKeySpec);
    }

    private X509Certificate generateCertificate(PublicKey publicKey, PrivateKey privateKey) throws Exception {
        Instant now = Instant.now();
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                CERT_SUBJECT,
                BigInteger.valueOf(now.toEpochMilli()),
                Date.from(now.minus(30, ChronoUnit.DAYS)),
                Date.from(now.plus(365, ChronoUnit.DAYS)),
                CERT_SUBJECT,
                publicKey);

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BC_PROVIDER)
                .build(privateKey);

        return new JcaX509CertificateConverter()
                .setProvider(BC_PROVIDER)
                .getCertificate(certBuilder.build(signer));
    }
}
