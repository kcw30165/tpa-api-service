package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.CurrencyDisplay;
import com.bct.ngtpa.apiservice.application.dto.ExportContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.FetchContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.MemberContext;
import com.bct.ngtpa.apiservice.application.dto.MemberContextPurpose;
import com.bct.ngtpa.apiservice.application.port.out.ApimContributionSummaryPort;
import com.bct.ngtpa.apiservice.application.port.out.MemberContextPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.config.CurrencyMappingProperties;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryDataset;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExportContributionSummaryServiceTest {

    private static final MemberContext CONTRIBUTIONS_CONTEXT = new MemberContext(
            "policyNo_for_contributions",
            "certNo_for_contributions",
            "userId_for_contributions",
            "trustCode_for_contributions",
            "schemeType_for_contributions");

    private static MemberContextPort memberContextPort() {
        return purpose -> Mono.just(CONTRIBUTIONS_CONTEXT);
    }

    @Test
    void usesResolvedReferenceDateMinusThirtySixMonthsAndResolvesCurrencies() {
        AtomicReference<FetchContributionSummaryCommand> captured = new AtomicReference<>();
        ApimContributionSummaryPort port = command -> {
            captured.set(command);
            return Mono.just(new ContributionSummaryDataset("HKD", List.of(), List.of()));
        };

        var currencyMappingService = new RecordingCurrencyMappingService();
        ReferenceDatePort referenceDatePort = () -> Mono.just(LocalDate.of(2026, 3, 31));

        var service = new ExportContributionSummaryService(port, currencyMappingService, referenceDatePort, memberContextPort());
        var result = service.execute(new ExportContributionSummaryCommand("JP", "MBR")).block();

        assertEquals("31/03/2023", captured.get().coverFrom());
        assertEquals("31/03/2026", captured.get().coverTo());
        assertEquals("policyNo_for_contributions", captured.get().policyNo());
        assertEquals("certNo_for_contributions", captured.get().certNo());
        assertEquals("userId_for_contributions", captured.get().userId());
        assertEquals("trustCode_for_contributions", captured.get().trustCode());
        assertEquals("schemeType_for_contributions", captured.get().schemeType());
        assertEquals(new CurrencyDisplay("HKD", "港元"), result.currencyDisplay());
        assertEquals("JP", currencyMappingService.envByLocale.get("en"));
        assertEquals("trustCode_for_contributions", currencyMappingService.trustCodeByLocale.get("en"));
        assertEquals("schemeType_for_contributions", currencyMappingService.schemeTypeByLocale.get("en"));
        assertEquals("JP", currencyMappingService.envByLocale.get("zh_HK"));
        assertEquals("trustCode_for_contributions", currencyMappingService.trustCodeByLocale.get("zh_HK"));
        assertEquals("schemeType_for_contributions", currencyMappingService.schemeTypeByLocale.get("zh_HK"));
    }

    @Test
    void resolvesMemberContextWithContributionsPurpose() {
        AtomicReference<MemberContextPurpose> capturedPurpose = new AtomicReference<>();
        MemberContextPort capturingPort = purpose -> {
            capturedPurpose.set(purpose);
            return Mono.just(CONTRIBUTIONS_CONTEXT);
        };

        var service = new ExportContributionSummaryService(
                command -> Mono.just(new ContributionSummaryDataset("HKD", List.of(), List.of())),
                new CurrencyMappingService(new CurrencyMappingProperties()),
                () -> Mono.just(LocalDate.of(2026, 3, 31)),
                capturingPort);

        service.execute(new ExportContributionSummaryCommand("JP", "MBR")).block();

        assertEquals(MemberContextPurpose.CONTRIBUTIONS, capturedPurpose.get());
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
