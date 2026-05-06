package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.CurrencyDisplay;
import com.bct.ngtpa.apiservice.application.dto.ExportContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.FetchContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.port.out.ApimContributionSummaryPort;
import com.bct.ngtpa.apiservice.config.CurrencyMappingProperties;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryDataset;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExportContributionSummaryServiceTest {

    @Test
    void usesDeterministicRefDateMinusThirtySixMonthsAndResolvesCurrencies() {
        AtomicReference<FetchContributionSummaryCommand> captured = new AtomicReference<>();
        ApimContributionSummaryPort port = command -> {
            captured.set(command);
            return Mono.just(new ContributionSummaryDataset("HKD", List.of(), List.of()));
        };

        var currencyMappingService = new RecordingCurrencyMappingService();

        var service = new ExportContributionSummaryService(port, currencyMappingService);
        var result = service.execute(new ExportContributionSummaryCommand("JP", "MBR")).block();

        assertEquals("01/10/2022", captured.get().coverFrom());
        assertEquals("01/10/2025", captured.get().coverTo());
        assertEquals("00000000217", captured.get().policyNo());
        assertEquals("95", captured.get().certNo());
        assertEquals("C402400A", captured.get().userId());
        assertEquals(new CurrencyDisplay("HKD", "港元"), result.currencyDisplay());
        assertEquals("JP", currencyMappingService.envByLocale.get("en"));
        assertEquals("", currencyMappingService.trustCodeByLocale.get("en"));
        assertEquals("", currencyMappingService.schemeTypeByLocale.get("en"));
        assertEquals("JP", currencyMappingService.envByLocale.get("zh_HK"));
        assertEquals("", currencyMappingService.trustCodeByLocale.get("zh_HK"));
        assertEquals("", currencyMappingService.schemeTypeByLocale.get("zh_HK"));
    }

    private static final class RecordingCurrencyMappingService extends CurrencyMappingService {

        private final java.util.Map<String, String> envByLocale = new java.util.HashMap<>();
        private final java.util.Map<String, String> trustCodeByLocale = new java.util.HashMap<>();
        private final java.util.Map<String, String> schemeTypeByLocale = new java.util.HashMap<>();

        private RecordingCurrencyMappingService() {
            super(new CurrencyMappingProperties());
        }

        @Override
        public String resolve(String locale, String code, String env, String trustCode, String schemeType) {
            envByLocale.put(locale, env);
            trustCodeByLocale.put(locale, trustCode);
            schemeTypeByLocale.put(locale, schemeType);
            return "zh_HK".equals(locale) ? "港元" : code;
        }
    }
}