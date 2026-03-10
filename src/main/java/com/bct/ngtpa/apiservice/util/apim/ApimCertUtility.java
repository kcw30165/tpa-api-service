package com.bct.ngtpa.apiservice.util.apim;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
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
        String pem = new String(Base64.getDecoder().decode(pemString), StandardCharsets.UTF_8);
        pem = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(pem);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
        return KeyFactory.getInstance(algorithm).generatePublic(spec);
    }

    public static PrivateKey getCustomerPrivateKey(String pemString) throws Exception {
        String pemBlock = new String(Base64.getDecoder().decode(pemString), StandardCharsets.UTF_8);
        String decodedPem = pemBlock.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
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
}
