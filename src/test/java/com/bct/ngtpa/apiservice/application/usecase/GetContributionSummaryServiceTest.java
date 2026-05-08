package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.adapter.in.web.config.ContributionWebDisplayConfig;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ContributionSummaryResponse;
import com.bct.ngtpa.apiservice.application.dto.CurrencyDisplay;
import com.bct.ngtpa.apiservice.application.dto.FetchContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.GetContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.MemberContext;
import com.bct.ngtpa.apiservice.application.dto.MemberContextPurpose;
import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import com.bct.ngtpa.apiservice.application.port.out.ApimContributionSummaryPort;
import com.bct.ngtpa.apiservice.application.port.out.CurrencyDisplayPort;
import com.bct.ngtpa.apiservice.application.port.out.MemberContextPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.domain.model.ContributionEntry;
import com.bct.ngtpa.apiservice.domain.model.ContributionLabels;
import com.bct.ngtpa.apiservice.domain.model.ContributionSource;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryDataset;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetContributionSummaryServiceTest {

    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 3, 31);

    private static final MemberContext CONTRIBUTIONS_CONTEXT = new MemberContext(
            "policyNo_for_contributions",
            "certNo_for_contributions",
            "userId_for_contributions",
            "trustCode_for_contributions",
            "schemeType_for_contributions");

    private static MemberContextPort memberContextPort() {
        return purpose -> Mono.just(CONTRIBUTIONS_CONTEXT);
    }

    private static ReferenceDatePort referenceDatePort() {
        return () -> Mono.just(REFERENCE_DATE);
    }

    @Test
    void acceptsValidDatesWithinReferenceWindowAndCallsApim() {
        AtomicReference<FetchContributionSummaryCommand> captured = new AtomicReference<>();
        AtomicInteger apimCalls = new AtomicInteger();
        ApimContributionSummaryPort port = command -> {
            apimCalls.incrementAndGet();
            captured.set(command);
            return Mono.just(sampleDataset());
        };

        var recordingPort = new RecordingCurrencyDisplayPort();
        var service = new GetContributionSummaryService(port, recordingPort, referenceDatePort(), memberContextPort());

        var result = service.execute(new GetContributionSummaryCommand("JP", "MBR", "01/01/2026", "31/03/2026")).block();

        assertEquals(1, apimCalls.get());
        assertEquals("01/01/2026", captured.get().coverFrom());
        assertEquals("31/03/2026", captured.get().coverTo());
        assertEquals("policyNo_for_contributions", captured.get().policyNo());
        assertEquals("certNo_for_contributions", captured.get().certNo());
        assertEquals("userId_for_contributions", captured.get().userId());
        assertEquals("trustCode_for_contributions", captured.get().trustCode());
        assertEquals("schemeType_for_contributions", captured.get().schemeType());
        assertEquals(new CurrencyDisplay("HKD", "港元"), result.currencyDisplay());
        assertEquals("HKD", recordingPort.capturedCode);
        assertEquals("JP", recordingPort.capturedEnv);
        assertEquals("trustCode_for_contributions", recordingPort.capturedTrustCode);
        assertEquals("schemeType_for_contributions", recordingPort.capturedSchemeType);
        assertEquals("HKD 24908.45", ContributionSummaryResponse
                .from(result, new ContributionWebDisplayConfig(
                    "Total Contributions",
                    "供款總額",
                    "Dealing date處理日期",
                    "Contribution Periods供款期",
                    "Total Contributions供款總額"
                ))
                .contributions().getFirst().totalContributionEn());
        assertEquals("港元 24908.45", ContributionSummaryResponse
                .from(result, new ContributionWebDisplayConfig(
                    "Total Contributions",
                    "供款總額",
                    "Dealing date處理日期",
                    "Contribution Periods供款期",
                    "Total Contributions供款總額"
                ))
                .contributions().getFirst().totalContributionZh());
    }

    @Test
    void resolvesMemberContextWithContributionsPurpose() {
        AtomicReference<MemberContextPurpose> capturedPurpose = new AtomicReference<>();
        MemberContextPort capturingPort = purpose -> {
            capturedPurpose.set(purpose);
            return Mono.just(CONTRIBUTIONS_CONTEXT);
        };

        CurrencyDisplayPort currencyDisplayPort = (code, env, trustCode, schemeType) ->
                new CurrencyDisplay(code, code);

        var service = new GetContributionSummaryService(
                command -> Mono.just(sampleDataset()),
                currencyDisplayPort,
                referenceDatePort(),
                capturingPort);

        service.execute(new GetContributionSummaryCommand("JP", "MBR", "01/01/2026", "31/03/2026")).block();

        assertEquals(MemberContextPurpose.CONTRIBUTIONS, capturedPurpose.get());
    }

    @Test
    void rejectsFromDateBeforeReferenceWindow() {
        AtomicInteger apimCalls = new AtomicInteger();
        CurrencyDisplayPort currencyDisplayPort = (code, env, trustCode, schemeType) ->
                new CurrencyDisplay(code, code);

        var service = new GetContributionSummaryService(
                command -> {
                    apimCalls.incrementAndGet();
                    return Mono.just(sampleDataset());
                },
                currencyDisplayPort,
                referenceDatePort(),
                memberContextPort());

        var ex = assertThrows(InvalidContributionRequestException.class,
                () -> service.execute(new GetContributionSummaryCommand("JP", "MBR", "30/03/2023", "31/03/2026")).block());

        assertEquals("fromDate and toDate must be within the range from ref-date minus 36 months to ref-date", ex.getMessage());
        assertEquals(0, apimCalls.get());
    }

    @Test
    void rejectsToDateAfterReferenceDate() {
        AtomicInteger apimCalls = new AtomicInteger();
        CurrencyDisplayPort currencyDisplayPort = (code, env, trustCode, schemeType) ->
                new CurrencyDisplay(code, code);

        var service = new GetContributionSummaryService(
                command -> {
                    apimCalls.incrementAndGet();
                    return Mono.just(sampleDataset());
                },
                currencyDisplayPort,
                referenceDatePort(),
                memberContextPort());

        var ex = assertThrows(InvalidContributionRequestException.class,
                () -> service.execute(new GetContributionSummaryCommand("JP", "MBR", "01/01/2026", "01/04/2026")).block());

        assertEquals("fromDate and toDate must be within the range from ref-date minus 36 months to ref-date", ex.getMessage());
        assertEquals(0, apimCalls.get());
    }

    @Test
    void rejectsFromDateAfterToDate() {
        AtomicInteger apimCalls = new AtomicInteger();
        CurrencyDisplayPort currencyDisplayPort = (code, env, trustCode, schemeType) ->
                new CurrencyDisplay(code, code);

        var service = new GetContributionSummaryService(
                command -> {
                    apimCalls.incrementAndGet();
                    return Mono.just(sampleDataset());
                },
                currencyDisplayPort,
                referenceDatePort(),
                memberContextPort());

        var ex = assertThrows(InvalidContributionRequestException.class,
                () -> service.execute(new GetContributionSummaryCommand("JP", "MBR", "31/03/2026", "01/01/2026")).block());

        assertEquals("fromDate must not be after toDate", ex.getMessage());
        assertEquals(0, apimCalls.get());
    }

    @Test
    void acceptsInclusiveBoundaryDates() {
        AtomicInteger apimCalls = new AtomicInteger();
        CurrencyDisplayPort currencyDisplayPort = (code, env, trustCode, schemeType) ->
                new CurrencyDisplay(code, code);

        var service = new GetContributionSummaryService(
                command -> {
                    apimCalls.incrementAndGet();
                    return Mono.just(sampleDataset());
                },
                currencyDisplayPort,
                referenceDatePort(),
                memberContextPort());

        service.execute(new GetContributionSummaryCommand("JP", "MBR", "31/03/2023", "31/03/2026")).block();

        assertEquals(1, apimCalls.get());
    }

    @Test
    void rejectsMissingOrInvalidDates() {
        CurrencyDisplayPort currencyDisplayPort = (code, env, trustCode, schemeType) ->
                new CurrencyDisplay(code, code);

        var service = new GetContributionSummaryService(
                command -> Mono.just(new ContributionSummaryDataset("", List.of(), List.of())),
                currencyDisplayPort,
                referenceDatePort(),
                memberContextPort());

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

    private static final class RecordingCurrencyDisplayPort implements CurrencyDisplayPort {

        String capturedCode;
        String capturedEnv;
        String capturedTrustCode;
        String capturedSchemeType;

        @Override
        public CurrencyDisplay resolveCurrencyDisplay(String code, String env, String trustCode, String schemeType) {
            this.capturedCode = code;
            this.capturedEnv = env;
            this.capturedTrustCode = trustCode;
            this.capturedSchemeType = schemeType;
            return new CurrencyDisplay(code, "港元");
        }
    }
}
