package com.bct.ngtpa.apiservice.adapter.out.apim.certificate;

import com.bct.ngtpa.apiservice.adapter.out.apim.ApimAppCertificateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApimCertificateHeaderProvider {

    private final ApimAppCertificateService apimAppCertificateService;

    public String getCertificateHeaderValue() {
        return apimAppCertificateService.getCertificateHeaderValue();
    }
}
