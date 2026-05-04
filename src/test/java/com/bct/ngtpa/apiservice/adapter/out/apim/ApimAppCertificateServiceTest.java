package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCertificateHelper;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.config.ApimProperties;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ApimAppCertificateServiceTest {

    private static final BouncyCastleProvider PROVIDER = new BouncyCastleProvider();

    private static KeyPair keyPair;

    @BeforeAll
    static void generateKeys() throws Exception {
        Security.addProvider(PROVIDER);
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        keyPair = keyPairGenerator.generateKeyPair();
    }

    @Test
    void initializesKeysAndCertificateHeaderWhenEncryptionEnabled() throws Exception {
        ApimProperties properties = encryptionProperties(true, pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()),
                pem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
        ApimCertificateHelper helper = new ApimCertificateHelper(PROVIDER);
        ApimAppCertificateService service = new ApimAppCertificateService(properties, helper);

        service.init();

        assertArrayEquals(keyPair.getPrivate().getEncoded(), service.getAppPrivateKey().getEncoded());
        assertArrayEquals(keyPair.getPublic().getEncoded(), service.getAppPublicKey().getEncoded());
        assertNotNull(service.getCertificateHeaderValue());

        byte[] certificateBytes = Base64.getDecoder().decode(service.getCertificateHeaderValue());
        String certificatePem = pem("CERTIFICATE", certificateBytes);
        assertArrayEquals(keyPair.getPublic().getEncoded(), helper.getPublicKeyFromCertificate(certificatePem).getEncoded());
    }

    @Test
    void leavesStateEmptyWhenEncryptionDisabled() {
        ApimAppCertificateService service = new ApimAppCertificateService(encryptionProperties(false, "ignored", "ignored"),
                new ApimCertificateHelper(PROVIDER));

        service.init();

        assertNull(service.getAppPrivateKey());
        assertNull(service.getAppPublicKey());
        assertNull(service.getCertificateHeaderValue());
    }

    @Test
    void leavesStateEmptyWhenPemValuesMissing() {
        ApimAppCertificateService service = new ApimAppCertificateService(encryptionProperties(true, " ", null),
                new ApimCertificateHelper(PROVIDER));

        service.init();

        assertNull(service.getAppPrivateKey());
        assertNull(service.getAppPublicKey());
        assertNull(service.getCertificateHeaderValue());
    }

    @Test
    void wrapsCertificateEncodingFailure() throws Exception {
        ApimProperties properties = encryptionProperties(true, "private", "public");
        ApimCertificateHelper helper = mock(ApimCertificateHelper.class);
        X509Certificate certificate = mock(X509Certificate.class);
        when(helper.getCustomerPublicKey("public", "RSA")).thenReturn(keyPair.getPublic());
        when(helper.getCustomerPrivateKey("private")).thenReturn(keyPair.getPrivate());
        when(helper.generateCertificate(keyPair.getPublic(), keyPair.getPrivate())).thenReturn(certificate);
        when(certificate.getEncoded()).thenThrow(new CertificateEncodingException("boom"));
        ApimAppCertificateService service = new ApimAppCertificateService(properties, helper);

        assertThrows(ApimCryptoException.class, service::init);
    }

    private static ApimProperties encryptionProperties(boolean enabled, String privatePem, String publicPem) {
        ApimProperties properties = new ApimProperties();
        properties.getEncryption().setEnabled(enabled);
        properties.getEncryption().setPrivateKeyPem(privatePem);
        properties.getEncryption().setPublicKeyPem(publicPem);
        return properties;
    }

    private static String pem(String type, byte[] content) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(content)
                + "\n-----END " + type + "-----\n";
    }
}