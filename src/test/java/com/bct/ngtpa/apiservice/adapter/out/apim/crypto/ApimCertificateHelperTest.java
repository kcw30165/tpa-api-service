package com.bct.ngtpa.apiservice.adapter.out.apim.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Base64;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.pkcs.RSAPrivateKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ApimCertificateHelperTest {

    private static final BouncyCastleProvider PROVIDER = new BouncyCastleProvider();

    private static KeyPair keyPair;

    private final ApimCertificateHelper helper = new ApimCertificateHelper(PROVIDER);

    @BeforeAll
    static void setUpCryptoMaterial() throws Exception {
        Security.addProvider(PROVIDER);
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        keyPair = keyPairGenerator.generateKeyPair();
    }

    @Test
    void generatesCertificateAndExtractsItsPublicKey() throws Exception {
        X509Certificate certificate = helper.generateCertificate(keyPair.getPublic(), keyPair.getPrivate());
        String certificatePem = pem("CERTIFICATE", certificate.getEncoded());

        assertArrayEquals(keyPair.getPublic().getEncoded(), helper.getPublicKeyFromCertificate(certificatePem).getEncoded());
        assertArrayEquals(keyPair.getPublic().getEncoded(), helper.getX509CertificateFromPem(certificatePem).getPublicKey().getEncoded());
        assertEquals("CN=Application", certificate.getSubjectX500Principal().getName());
    }

    @Test
    void parsesPublicKeyFromPemAndBase64EncodedPem() {
        String publicPem = pem("PUBLIC KEY", keyPair.getPublic().getEncoded());
        String base64EncodedPem = Base64.getEncoder().encodeToString(publicPem.getBytes(StandardCharsets.UTF_8));
        String rawBase64Der = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        assertArrayEquals(keyPair.getPublic().getEncoded(), helper.getCustomerPublicKey(publicPem, "RSA").getEncoded());
        assertArrayEquals(keyPair.getPublic().getEncoded(), helper.getCustomerPublicKey(base64EncodedPem, "RSA").getEncoded());
        assertArrayEquals(keyPair.getPublic().getEncoded(), helper.getCustomerPublicKey(rawBase64Der, "RSA").getEncoded());
    }

    @Test
    void parsesPrivateKeyFromPkcs8AndPkcs1Pem() throws Exception {
        String privatePem = pem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
        String escapedPrivatePem = privatePem.replace("\n", "\\n");
        String rawBase64Der = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());

        PrivateKeyInfo privateKeyInfo = PrivateKeyInfo.getInstance(keyPair.getPrivate().getEncoded());
        RSAPrivateKey rsaPrivateKey = RSAPrivateKey.getInstance(privateKeyInfo.parsePrivateKey());
        String pkcs1Pem = pem("RSA PRIVATE KEY", rsaPrivateKey.getEncoded());

        assertArrayEquals(keyPair.getPrivate().getEncoded(), helper.getCustomerPrivateKey(privatePem).getEncoded());
        assertArrayEquals(keyPair.getPrivate().getEncoded(), helper.getCustomerPrivateKey(escapedPrivatePem).getEncoded());
        assertArrayEquals(keyPair.getPrivate().getEncoded(), helper.getCustomerPrivateKey(rawBase64Der).getEncoded());
        assertEquals("RSA", helper.getCustomerPrivateKey(pkcs1Pem).getAlgorithm());
    }

    @Test
    void wrapsCertificateGenerationFailuresInCryptoException() {
        assertThrows(ApimCryptoException.class, () -> helper.generateCertificate(keyPair.getPublic(), null));
    }

    @Test
    void wrapsInvalidInputsInCryptoExceptions() {
        assertThrows(ApimCryptoException.class, () -> helper.getCustomerPublicKey("not-a-key", "RSA"));
        assertThrows(ApimCryptoException.class, () -> helper.getCustomerPrivateKey("not-a-key"));
        assertThrows(ApimCryptoException.class, () -> helper.getPublicKeyFromCertificate("not-a-cert"));
        assertThrows(ApimCryptoException.class, () -> helper.getX509CertificateFromPem("not-a-cert"));
    }

    private static String pem(String type, byte[] content) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(content)
                + "\n-----END " + type + "-----\n";
    }
}