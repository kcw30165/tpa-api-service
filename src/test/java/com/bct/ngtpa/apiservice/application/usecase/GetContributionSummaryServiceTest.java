package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ContributionActions;
import com.bct.ngtpa.apiservice.application.dto.CurrencyDisplay;
import com.bct.ngtpa.apiservice.application.dto.FetchContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.GetContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import com.bct.ngtpa.apiservice.application.port.out.ApimContributionSummaryPort;
import com.bct.ngtpa.apiservice.application.port.out.ContributionActionPermissionPort;
import com.bct.ngtpa.apiservice.application.port.out.CurrencyDisplayPort;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextPort;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GetContributionSummaryServiceTest {

    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 3, 31);

    private static final PortalAccessContext CONTRIBUTIONS_CONTEXT = new PortalAccessContext(
            new ActorContext("userId_for_contributions", "SELF"),
            new AccountContext(
                    "contributions",
                    "JP",
                    "policyNo_for_contributions",
                    "certNo_for_contributions",
                    "trustCode_for_contributions",
                    "schemeType_for_contributions",
                    TermStatus.BLANK,
                    null));

    private static PortalAccessContextPort portalAccessContextPort() {
        return accountRef -> Mono.just(CONTRIBUTIONS_CONTEXT);
    }

    private static ReferenceDatePort referenceDatePort() {
        return () -> Mono.just(REFERENCE_DATE);
    }

    private static ContributionActionPermissionPort actionPermissionPort() {
        return () -> new ContributionActions(true);
    }

    private GetContributionSummaryService serviceWith(ApimContributionSummaryPort apimPort,
                                                       CurrencyDisplayPort currencyPort) {
        return new GetContributionSummaryService(
                apimPort, currencyPort, referenceDatePort(), portalAccessContextPort(), actionPermissionPort());
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
        var service = serviceWith(port, recordingPort);

        var result = service.execute(new GetContributionSummaryCommand(
                "01/01/2026", "31/03/2026", "en", 1, 99999)).block();

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
        assertEquals("JP", recordingPort.capturedAccountEnv);
        assertEquals("trustCode_for_contributions", recordingPort.capturedTrustCode);
        assertEquals("schemeType_for_contributions", recordingPort.capturedSchemeType);
        assertNotNull(result.actions());
        assertTrue(result.actions().exportEnabled());
        assertEquals("trustCode_for_contributions", result.trustCode());
        assertEquals("schemeType_for_contributions", result.schemeType());
        assertEquals("JP", result.accountEnv());
    }

    @Test
    void resolvesPortalAccessContextWithCommandAccountRef() {
        AtomicReference<String> capturedRef = new AtomicReference<>();
        PortalAccessContextPort capturingPort = accountRef -> {
            capturedRef.set(accountRef);
            return Mono.just(CONTRIBUTIONS_CONTEXT);
        };

        CurrencyDisplayPort currencyDisplayPort = (code, accountEnv, trustCode, schemeType) ->
                new CurrencyDisplay(code, code);

        var service = new GetContributionSummaryService(
                command -> Mono.just(sampleDataset()),
                currencyDisplayPort,
                referenceDatePort(),
                capturingPort,
                actionPermissionPort());

        service.execute(new GetContributionSummaryCommand(
        "01/01/2026", "31/03/2026", "en", 1, 99999, "ACC-123")).block();

    assertEquals("ACC-123", capturedRef.get());
    }

    @Test
    void rejectsFromDateBeforeReferenceWindow() {
        AtomicInteger apimCalls = new AtomicInteger();
        var service = serviceWith(command -> {
            apimCalls.incrementAndGet();
            return Mono.just(sampleDataset());
        }, (code, accountEnv, trustCode, schemeType) -> new CurrencyDisplay(code, code));

        var ex = assertThrows(InvalidContributionRequestException.class,
                () -> service.execute(new GetContributionSummaryCommand(
                        "30/03/2023", "31/03/2026", "en", 1, 99999)).block());

        assertEquals("fromDate and toDate must be within the range from ref-date minus 36 months to ref-date", ex.getMessage());
        assertEquals(0, apimCalls.get());
    }

    @Test
    void rejectsToDateAfterReferenceDate() {
        AtomicInteger apimCalls = new AtomicInteger();
        var service = serviceWith(command -> {
            apimCalls.incrementAndGet();
            return Mono.just(sampleDataset());
        }, (code, accountEnv, trustCode, schemeType) -> new CurrencyDisplay(code, code));

        var ex = assertThrows(InvalidContributionRequestException.class,
                () -> service.execute(new GetContributionSummaryCommand(
                        "01/01/2026", "01/04/2026", "en", 1, 99999)).block());

        assertEquals("fromDate and toDate must be within the range from ref-date minus 36 months to ref-date", ex.getMessage());
        assertEquals(0, apimCalls.get());
    }

    @Test
    void rejectsFromDateAfterToDate() {
        AtomicInteger apimCalls = new AtomicInteger();
        var service = serviceWith(command -> {
            apimCalls.incrementAndGet();
            return Mono.just(sampleDataset());
        }, (code, accountEnv, trustCode, schemeType) -> new CurrencyDisplay(code, code));

        var ex = assertThrows(InvalidContributionRequestException.class,
                () -> service.execute(new GetContributionSummaryCommand(
                        "31/03/2026", "01/01/2026", "en", 1, 99999)).block());

        assertEquals("fromDate must not be after toDate", ex.getMessage());
        assertEquals(0, apimCalls.get());
    }

    @Test
    void acceptsInclusiveBoundaryDates() {
        AtomicInteger apimCalls = new AtomicInteger();
        var service = serviceWith(command -> {
            apimCalls.incrementAndGet();
            return Mono.just(sampleDataset());
        }, (code, accountEnv, trustCode, schemeType) -> new CurrencyDisplay(code, code));

        service.execute(new GetContributionSummaryCommand(
                "31/03/2023", "31/03/2026", "en", 1, 99999)).block();

        assertEquals(1, apimCalls.get());
    }

    @Test
    void rejectsMissingOrInvalidDates() {
        var service = serviceWith(
                command -> Mono.just(new ContributionSummaryDataset("", List.of(), List.of())),
                (code, accountEnv, trustCode, schemeType) -> new CurrencyDisplay(code, code));

        assertEquals("fromDate must be provided in dd/MM/yyyy format", assertThrows(
                InvalidContributionRequestException.class,
                () -> service.execute(new GetContributionSummaryCommand(
                        null, "05/05/2026", "en", 1, 99999)).block()).getMessage());
        assertEquals("toDate must be provided in dd/MM/yyyy format", assertThrows(
                InvalidContributionRequestException.class,
                () -> service.execute(new GetContributionSummaryCommand(
                        "05/04/2026", null, "en", 1, 99999)).block()).getMessage());
        assertEquals("fromDate must be provided in dd/MM/yyyy format", assertThrows(
                InvalidContributionRequestException.class,
                () -> service.execute(new GetContributionSummaryCommand(
                        "2026-04-05", "05/05/2026", "en", 1, 99999)).block()).getMessage());
        assertEquals("toDate must be provided in dd/MM/yyyy format", assertThrows(
                InvalidContributionRequestException.class,
                () -> service.execute(new GetContributionSummaryCommand(
                        "05/04/2026", "2026-05-05", "en", 1, 99999)).block()).getMessage());
    }

    @Test
    void rejectsPageLessThanOrEqualZero() {
        var service = serviceWith(
                command -> Mono.just(sampleDataset()),
                (code, accountEnv, trustCode, schemeType) -> new CurrencyDisplay(code, code));

        var ex = assertThrows(InvalidContributionRequestException.class,
                () -> service.execute(new GetContributionSummaryCommand(
                        "01/01/2026", "31/03/2026", "en", 0, 99999)).block());
        assertEquals("page must be greater than 0", ex.getMessage());
    }

    @Test
    void rejectsPageSizeLessThanOrEqualZero() {
        var service = serviceWith(
                command -> Mono.just(sampleDataset()),
                (code, accountEnv, trustCode, schemeType) -> new CurrencyDisplay(code, code));

        var ex = assertThrows(InvalidContributionRequestException.class,
                () -> service.execute(new GetContributionSummaryCommand(
                        "01/01/2026", "31/03/2026", "en", 1, 0)).block());
        assertEquals("pageSize must be greater than 0", ex.getMessage());
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
        String capturedAccountEnv;
        String capturedTrustCode;
        String capturedSchemeType;

        @Override
        public CurrencyDisplay resolveCurrencyDisplay(String code, String accountEnv, String trustCode, String schemeType) {
            this.capturedCode = code;
            this.capturedAccountEnv = accountEnv;
            this.capturedTrustCode = trustCode;
            this.capturedSchemeType = schemeType;
            return new CurrencyDisplay(code, "港元");
        }
    }
}


