package com.bct.ngtpa.apiservice.adapter.out.apim.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

class ApimCryptoConfigTest {

    @Test
    void bouncyCastleProviderIsAddedOnlyOnce() {
        ApimCryptoConfig config = new ApimCryptoConfig();
        String providerName = new BouncyCastleProvider().getName();

        Security.removeProvider(providerName);

        BouncyCastleProvider first = config.bouncyCastleProvider();
        BouncyCastleProvider second = config.bouncyCastleProvider();

        assertEquals(first.getName(), second.getName());
        assertNotNull(Security.getProvider(first.getName()));
    }
}
