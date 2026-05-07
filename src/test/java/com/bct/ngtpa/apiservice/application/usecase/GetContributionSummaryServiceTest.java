package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ContributionSummaryResponse;
import com.bct.ngtpa.apiservice.application.dto.CurrencyDisplay;
import com.bct.ngtpa.apiservice.application.dto.FetchContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.GetContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import com.bct.ngtpa.apiservice.application.port.out.ApimContributionSummaryPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.config.ContributionSummaryProperties;
import com.bct.ngtpa.apiservice.config.CurrencyMappingProperties;
import com.bct.ngtpa.apiservice.domain.model.ContributionEntry;
import com.bct.ngtpa.apiservice.domain.model.ContributionLabels;
import com.bct.ngtpa.apiservice.domain.model.ContributionSource;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryDataset;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetContributionSummaryServiceTest {

    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 3, 31);

    @Test
    void acceptsValidDatesWithinReferenceWindowAndCallsApim() {
        AtomicReference<FetchContributionSummaryCommand> captured = new AtomicReference<>();
        AtomicInteger apimCalls = new AtomicInteger();
        ApimContributionSummaryPort port = command -> {
            apimCalls.incrementAndGet();
            captured.set(command);
            return Mono.just(sampleDataset());
        };

        var currencyMappingService = new RecordingCurrencyMappingService();
        var service = new GetContributionSummaryService(port, currencyMappingService, referenceDatePort());

        var result = service.execute(new GetContributionSummaryCommand("JP", "MBR", "01/01/2026", "31/03/2026")).block();

        assertEquals(1, apimCalls.get());
        assertEquals("01/01/2026", captured.get().coverFrom());
        assertEquals("31/03/2026", captured.get().coverTo());
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
        assertEquals("HKD 24908.45", ContributionSummaryResponse
                .from(result, new ContributionSummaryProperties())
                .contributions().getFirst().totalContributionEn());
        assertEquals("港元 24908.45", ContributionSummaryResponse
                .from(result, new ContributionSummaryProperties())
                .contributions().getFirst().totalContributionZh());
    }

    @Test
    void rejectsFromDateBeforeReferenceWindow() {
        AtomicInteger apimCalls = new AtomicInteger();
        var service = new GetContributionSummaryService(
                command -> {
                    apimCalls.incrementAndGet();
                    return Mono.just(sampleDataset());
                },
                new CurrencyMappingService(new CurrencyMappingProperties()),
                referenceDatePort());

        var ex = assertThrows(InvalidContributionRequestException.class,
                () -> service.execute(new GetContributionSummaryCommand("JP", "MBR", "30/03/2023", "31/03/2026")).block());

        assertEquals("fromDate and toDate must be within the range from ref-date minus 36 months to ref-date", ex.getMessage());
        assertEquals(0, apimCalls.get());
    }

    @Test
    void rejectsToDateAfterReferenceDate() {
        AtomicInteger apimCalls = new AtomicInteger();
        var service = new GetContributionSummaryService(
                command -> {
                    apimCalls.incrementAndGet();
                    return Mono.just(sampleDataset());
                },
                new CurrencyMappingService(new CurrencyMappingProperties()),
                referenceDatePort());

        var ex = assertThrows(InvalidContributionRequestException.class,
                () -> service.execute(new GetContributionSummaryCommand("JP", "MBR", "01/01/2026", "01/04/2026")).block());

        assertEquals("fromDate and toDate must be within the range from ref-date minus 36 months to ref-date", ex.getMessage());
        assertEquals(0, apimCalls.get());
    }

    @Test
    void rejectsFromDateAfterToDate() {
        AtomicInteger apimCalls = new AtomicInteger();
        var service = new GetContributionSummaryService(
                command -> {
                    apimCalls.incrementAndGet();
                    return Mono.just(sampleDataset());
                },
                new CurrencyMappingService(new CurrencyMappingProperties()),
                referenceDatePort());

        var ex = assertThrows(InvalidContributionRequestException.class,
                () -> service.execute(new GetContributionSummaryCommand("JP", "MBR", "31/03/2026", "01/01/2026")).block());

        assertEquals("fromDate must not be after toDate", ex.getMessage());
        assertEquals(0, apimCalls.get());
    }

    @Test
    void acceptsInclusiveBoundaryDates() {
        AtomicInteger apimCalls = new AtomicInteger();
        var service = new GetContributionSummaryService(
                command -> {
                    apimCalls.incrementAndGet();
                    return Mono.just(sampleDataset());
                },
                new CurrencyMappingService(new CurrencyMappingProperties()),
                referenceDatePort());

        service.execute(new GetContributionSummaryCommand("JP", "MBR", "31/03/2023", "31/03/2026")).block();

        assertEquals(1, apimCalls.get());
    }

    @Test
    void rejectsMissingOrInvalidDates() {
        var service = new GetContributionSummaryService(
                command -> Mono.just(new ContributionSummaryDataset("", List.of(), List.of())),
                new CurrencyMappingService(new CurrencyMappingProperties()),
                referenceDatePort());

        assertEquals("fromDate must be provided in dd/MM/yyyy format", assertThrows(
                InvalidContributionRequestException.class,
                () -> service.execute(new GetContributionSummaryCommand("JP", "MBR", null, "05/05/2026")).block()).getMessage());
        assertEquals("toDate must be provided in dd/MM/yyyy format", assertThrows(
                InvalidContributionRequestException.class,
                () -> service.execute(new GetContributionSummaryCommand("JP", "MBR", "05/04/2026", null)).block()).getMessage());
        assertEquals("fromDate must be provided in dd/MM/yyyy format", assertThrows(
                InvalidContributionRequestException.class,
                () -> service.execute(new GetContributionSummaryCommand("JP", "MBR", "2026-04-05", "05/05/2026")).block()).getMessage());
        assertEquals("toDate must be provided in dd/MM/yyyy format", assertThrows(
                InvalidContributionRequestException.class,
                () -> service.execute(new GetContributionSummaryCommand("JP", "MBR", "05/04/2026", "2026-05-05")).block()).getMessage());
    }

    private static ReferenceDatePort referenceDatePort() {
        return () -> Mono.just(REFERENCE_DATE);
    }

    private static ContributionSummaryDataset sampleDataset() {
        return new ContributionSummaryDataset(
                "HKD",
                List.of(
                        new ContributionSource("EE", new ContributionLabels("Member", ""), 20),
                        new ContributionSource("ER", new ContributionLabels("Company", ""), 10)),
                List.of(
                        new ContributionEntry("ER", "05/04/2026", "31/03/2026", "01/03/2026", new BigDecimal("17791.75")),
                        new ContributionEntry("EE", "05/04/2026", "31/03/2026", "01/03/2026", new BigDecimal("7116.7"))));
    }

    private static final class RecordingCurrencyMappingService extends CurrencyMappingService {

        private final Map<String, String> envByLocale = new HashMap<>();
        private final Map<String, String> trustCodeByLocale = new HashMap<>();
        private final Map<String, String> schemeTypeByLocale = new HashMap<>();

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