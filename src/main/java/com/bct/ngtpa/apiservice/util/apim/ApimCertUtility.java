package com.bct.ngtpa.apiservice.util.apim;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
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

public final class ApimCertUtility {
    private static final String BC_PROVIDER = "BC";
    private static final String ASCII_KEY_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int AES_KEY_LENGTH = 32;
    private static final X500Name CERT_SUBJECT = new X500Name("CN=Application");

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private ApimCertUtility() {
    }

    public static X509Certificate generateCert(PublicKey publicKey, PrivateKey privateKey) throws Exception {
        Instant now = Instant.now();
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                CERT_SUBJECT,
                java.math.BigInteger.valueOf(now.toEpochMilli()),
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

    public static PublicKey getCustomerPublicKey(String pemString, String algorithm) throws Exception {
        String pemDocument = normalizePemDocument(pemString);
        if (pemDocument.contains("-----BEGIN PUBLIC KEY-----")
                || pemDocument.contains("-----BEGIN RSA PUBLIC KEY-----")) {
            try (PEMParser pemParser = new PEMParser(new StringReader(pemDocument))) {
                Object pemObject = pemParser.readObject();
                JcaPEMKeyConverter keyConverter = new JcaPEMKeyConverter().setProvider(BC_PROVIDER);
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
    }

    public static PrivateKey getCustomerPrivateKey(String pemString) throws Exception {
        String pemDocument = normalizePemDocument(pemString);
        if (pemDocument.contains("-----BEGIN PRIVATE KEY-----")
                || pemDocument.contains("-----BEGIN RSA PRIVATE KEY-----")) {
            try (PEMParser pemParser = new PEMParser(new StringReader(pemDocument))) {
                Object pemObject = pemParser.readObject();
                JcaPEMKeyConverter keyConverter = new JcaPEMKeyConverter().setProvider(BC_PROVIDER);
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
    }

    public static SecretKey getAesKey() {
        String asciiKey = generateAsciiKey(AES_KEY_LENGTH);
        byte[] keyBytes = asciiKey.getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(keyBytes, 0, keyBytes.length, "AES");
    }

    public static PublicKey getPublicKeyFromCert(String pemCert) throws Exception {
        String cleanedCert = pemCert
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s", "");
        byte[] certBytes = Base64.getDecoder().decode(cleanedCert);
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        X509Certificate certificate = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certBytes));
        return certificate.getPublicKey();
    }

    private static String generateAsciiKey(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            builder.append(ASCII_KEY_CHARS.charAt(random.nextInt(ASCII_KEY_CHARS.length())));
        }
        return builder.toString();
    }

    private static String normalizePemDocument(String pemInput) {
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
            // Input is not base64-encoded PEM; fall through.
        }

        return normalizedInput;
    }

    private static String stripPemMarkers(String pemValue, String beginMarker, String endMarker) {
        return pemValue.replace(beginMarker, "")
                .replace(endMarker, "")
                .replaceAll("\\s", "");
    }
}
