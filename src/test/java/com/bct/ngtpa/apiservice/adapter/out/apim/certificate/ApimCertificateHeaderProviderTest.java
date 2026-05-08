package com.bct.ngtpa.apiservice.adapter.out.apim.certificate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimAppCertificateService;
import org.junit.jupiter.api.Test;

class ApimCertificateHeaderProviderTest {

    private final ApimAppCertificateService apimAppCertificateService = mock(ApimAppCertificateService.class);
    private final ApimCertificateHeaderProvider provider = new ApimCertificateHeaderProvider(apimAppCertificateService);

    @Test
    void returnsCertificateHeaderValueFromUnderlyingService() {
        when(apimAppCertificateService.getCertificateHeaderValue()).thenReturn("encoded-certificate");

        String value = provider.getCertificateHeaderValue();

        assertEquals("encoded-certificate", value);
    }

    @Test
    void returnsNullWhenUnderlyingServiceReturnsNull() {
        when(apimAppCertificateService.getCertificateHeaderValue()).thenReturn(null);

        String value = provider.getCertificateHeaderValue();

        assertNull(value);
    }
}
