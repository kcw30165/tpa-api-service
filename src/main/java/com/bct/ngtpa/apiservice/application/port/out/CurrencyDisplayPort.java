package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.CurrencyDisplay;

public interface CurrencyDisplayPort {
    CurrencyDisplay resolveCurrencyDisplay(
        String code,
        String env,
        String trustCode,
        String schemeType
    );
}
