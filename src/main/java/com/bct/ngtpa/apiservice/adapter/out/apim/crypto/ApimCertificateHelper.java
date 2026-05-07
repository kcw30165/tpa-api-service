package com.bct.ngtpa.apiservice.adapter.out.apim.crypto;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.pkcs.RSAPrivateKey;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApimCertificateHelper {
    private static final X500Name CERT_SUBJECT = new X500Name("CN=Application");

    private final BouncyCastleProvider bouncyCastleProvider;

    public X509Certificate generateCertificate(PublicKey publicKey, PrivateKey privateKey) {
        try {
            Instant now = Instant.now();
            X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                    CERT_SUBJECT,
                    java.math.BigInteger.valueOf(now.toEpochMilli()),
                    Date.from(now.minus(30, ChronoUnit.DAYS)),
                    Date.from(now.plus(365, ChronoUnit.DAYS)),
                    CERT_SUBJECT,
                    publicKey);

            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                    .setProvider(bouncyCastleProvider.getName())
                    .build(privateKey);

            return new JcaX509CertificateConverter()
                    .setProvider(bouncyCastleProvider.getName())
                    .getCertificate(certBuilder.build(signer));
        } catch (Exception ex) {
            throw new ApimCryptoException("Unable to generate APIM application certificate.", ex);
        }
    }

    public PublicKey getCustomerPublicKey(String pemString, String algorithm) {
        try {
            String pemDocument = normalizePemDocument(pemString);
            if (pemDocument.contains("-----BEGIN PUBLIC KEY-----")
                    || pemDocument.contains("-----BEGIN RSA PUBLIC KEY-----")) {
                try (PEMParser pemParser = new PEMParser(new StringReader(pemDocument))) {
                    Object pemObject = pemParser.readObject();
                    JcaPEMKeyConverter keyConverter = new JcaPEMKeyConverter()
                            .setProvider(bouncyCastleProvider.getName());
                    if (pemObject instanceof SubjectPublicKeyInfo subjectPublicKeyInfo) {
                        return keyConverter.getPublicKey(subjectPublicKeyInfo);
                    }
                    if (pemObject instanceof PEMKeyPair pemKeyPair) {
                        return keyConverter.getKeyPair(pemKeyPair).getPublic();
                    }
                }
            }

            String pem = stripPemMarkers(pemDocument, "-----BEGIN PUBLIC KEY-----", "-----END PUBLIC KEY-----");
            byte[] der = Base64.getDecoder().decode(pem);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
            return KeyFactory.getInstance(algorithm).generatePublic(spec);
        } catch (Exception ex) {
            throw new ApimCryptoException("Unable to parse APIM public key.", ex);
        }
    }

    public PrivateKey getCustomerPrivateKey(String pemString) {
        try {
            String pemDocument = normalizePemDocument(pemString);
            if (pemDocument.contains("-----BEGIN PRIVATE KEY-----")
                    || pemDocument.contains("-----BEGIN RSA PRIVATE KEY-----")) {
                try (PEMParser pemParser = new PEMParser(new StringReader(pemDocument))) {
                    Object pemObject = pemParser.readObject();
                    JcaPEMKeyConverter keyConverter = new JcaPEMKeyConverter()
                            .setProvider(bouncyCastleProvider.getName());
                    if (pemObject instanceof PEMKeyPair pemKeyPair) {
                        return keyConverter.getKeyPair(pemKeyPair).getPrivate();
                    }
                    if (pemObject instanceof PrivateKeyInfo privateKeyInfo) {
                        return keyConverter.getPrivateKey(privateKeyInfo);
                    }
                    if (pemObject instanceof RSAPrivateKey rsaPrivateKey) {
                        RSAPrivateCrtKeySpec keySpec = new RSAPrivateCrtKeySpec(
                                rsaPrivateKey.getModulus(),
                                rsaPrivateKey.getPublicExponent(),
                                rsaPrivateKey.getPrivateExponent(),
                                rsaPrivateKey.getPrime1(),
                                rsaPrivateKey.getPrime2(),
                                rsaPrivateKey.getExponent1(),
                                rsaPrivateKey.getExponent2(),
                                rsaPrivateKey.getCoefficient());
                        return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
                    }
                }
            }

            String decodedPem = stripPemMarkers(pemDocument, "-----BEGIN PRIVATE KEY-----", "-----END PRIVATE KEY-----");
            byte[] der = Base64.getDecoder().decode(decodedPem);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception ex) {
            throw new ApimCryptoException("Unable to parse APIM private key.", ex);
        }
    }

    public PublicKey getPublicKeyFromCertificate(String pemCert) {
        try {
            String cleanedCert = pemCert
                    .replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replaceAll("\\s", "");
            byte[] certBytes = Base64.getDecoder().decode(cleanedCert);
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certBytes));
            return certificate.getPublicKey();
        } catch (Exception ex) {
            throw new ApimCryptoException("Failed to parse BCT public certificate.", ex);
        }
    }

    public X509Certificate getX509CertificateFromPem(String pemCert) {
        try {
            String cleanedCert = pemCert
                    .replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replaceAll("\\s", "");
            byte[] certBytes = Base64.getDecoder().decode(cleanedCert);
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certBytes));
        } catch (Exception ex) {
            throw new ApimCryptoException("Failed to parse BCT X509 certificate.", ex);
        }
    }

    private String normalizePemDocument(String pemInput) {
        String normalizedInput = pemInput
                .trim()
                .replace("\\n", "\n")
                .replace("\\r", "\r");

        if (normalizedInput.contains("-----BEGIN ")) {
            return normalizedInput;
        }

        try {
            String decodedValue = new String(Base64.getDecoder().decode(normalizedInput), StandardCharsets.UTF_8);
            if (decodedValue.contains("-----BEGIN ")) {
                return decodedValue;
            }
        } catch (IllegalArgumentException ex) {
            return normalizedInput;
        }

        return normalizedInput;
    }

    private String stripPemMarkers(String pemValue, String beginMarker, String endMarker) {
        return pemValue.replace(beginMarker, "")
                .replace(endMarker, "")
                .replaceAll("\\s", "");
    }
}