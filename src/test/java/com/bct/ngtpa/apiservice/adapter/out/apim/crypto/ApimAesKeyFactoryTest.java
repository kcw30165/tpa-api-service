package com.bct.ngtpa.apiservice.adapter.out.apim.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class ApimAesKeyFactoryTest {

    @Test
    void generatesAsciiBackedAesKey() {
        SecretKey key = new ApimAesKeyFactory().generateKey();
        String asciiValue = new String(key.getEncoded(), StandardCharsets.UTF_8);

        assertEquals("AES", key.getAlgorithm());
        assertEquals(32, key.getEncoded().length);
        assertTrue(asciiValue.matches("[A-Za-z0-9]{32}"));
    }
}