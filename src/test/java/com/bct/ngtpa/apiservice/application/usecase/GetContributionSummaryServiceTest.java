package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.CurrencyDisplay;
import com.bct.ngtpa.apiservice.application.dto.GetContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import com.bct.ngtpa.apiservice.application.port.out.ApimContributionSummaryPort;
import com.bct.ngtpa.apiservice.config.CurrencyMappingProperties;
import com.bct.ngtpa.apiservice.domain.model.ContributionEntry;
import com.bct.ngtpa.apiservice.domain.model.ContributionLabels;
import com.bct.ngtpa.apiservice.domain.model.ContributionSource;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryDataset;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetContributionSummaryServiceTest {

    @Test
        void validatesDatesEnrichesCommandBuildsReportAndResolvesCurrencies() {
        AtomicReference<com.bct.ngtpa.apiservice.application.dto.FetchContributionSummaryCommand> captured = new AtomicReference<>();
        ApimContributionSummaryPort port = command -> {
            captured.set(command);
            return Mono.just(new ContributionSummaryDataset(
                    "HKD",
                    List.of(
                            new ContributionSource("EE", new ContributionLabels("Member", ""), 20),
                            new ContributionSource("ER", new ContributionLabels("Company", ""), 10)),
                    List.of(
                            new ContributionEntry("ER", "05/04/2026", "05/05/2026", "01/03/2026", new BigDecimal("17791.75")),
                            new ContributionEntry("EE", "05/04/2026", "05/05/2026", "01/03/2026", new BigDecimal("7116.7")))));
        };

        var currencyMappingService = new RecordingCurrencyMappingService();

        var service = new GetContributionSummaryService(port, currencyMappingService);
        var result = service.execute(new GetContributionSummaryCommand("JP", "MBR", "05/04/2026", "05/05/2026")).block();

        assertEquals("05/04/2026", captured.get().coverFrom());
        assertEquals("05/05/2026", captured.get().coverTo());
        assertEquals("00000000217", captured.get().policyNo());
        assertEquals("95", captured.get().certNo());
        assertEquals("C402400A", captured.get().userId());
        assertEquals("", captured.get().trustCode());
        assertEquals("", captured.get().schemeType());
        assertEquals(new CurrencyDisplay("HKD", "港元"), result.currencyDisplay());
        assertEquals("JP", currencyMappingService.envByLocale.get("en"));
        assertEquals("", currencyMappingService.trustCodeByLocale.get("en"));
        assertEquals("", currencyMappingService.schemeTypeByLocale.get("en"));
        assertEquals("JP", currencyMappingService.envByLocale.get("zh_HK"));
        assertEquals("", currencyMappingService.trustCodeByLocale.get("zh_HK"));
        assertEquals("", currencyMappingService.schemeTypeByLocale.get("zh_HK"));
        assertEquals("HKD 24908.45", com.bct.ngtpa.apiservice.adapter.in.web.response.ContributionSummaryResponse
                .from(result, new com.bct.ngtpa.apiservice.config.ContributionSummaryProperties())
                .contributions().getFirst().totalContribution());
        assertEquals("港元 24908.45", com.bct.ngtpa.apiservice.adapter.in.web.response.ContributionSummaryResponse
                .from(result, new com.bct.ngtpa.apiservice.config.ContributionSummaryProperties())
                .contributions().getFirst().totalContributionZh());
    }

    @Test
    void rejectsMissingOrInvalidDates() {
        ApimContributionSummaryPort port = command -> Mono.just(new ContributionSummaryDataset("", List.of(), List.of()));
                var service = new GetContributionSummaryService(port, new CurrencyMappingService(new CurrencyMappingProperties()));

        assertThrows(InvalidContributionRequestException.class,
                () -> service.execute(new GetContributionSummaryCommand("JP", "MBR", null, "05/05/2026")).block());
        assertThrows(InvalidContributionRequestException.class,
                () -> service.execute(new GetContributionSummaryCommand("JP", "MBR", "05/04/2026", "2026-05-05")).block());
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