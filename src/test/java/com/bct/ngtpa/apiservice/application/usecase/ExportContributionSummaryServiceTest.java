package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.CurrencyDisplay;
import com.bct.ngtpa.apiservice.application.dto.ExportContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.FetchContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.port.out.ApimContributionSummaryPort;
import com.bct.ngtpa.apiservice.application.port.out.CurrencyDisplayPort;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryDataset;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExportContributionSummaryServiceTest {

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

    private static CurrentPortalAccessContextResolver currentPortalAccessContextResolver() {
        return new CurrentPortalAccessContextResolver() {
            @Override
            public Mono<PortalAccessContext> current() {
                return Mono.just(CONTRIBUTIONS_CONTEXT);
            }

            @Override
            public Mono<PortalAccessContext> currentOrEmpty() {
                return Mono.just(CONTRIBUTIONS_CONTEXT);
            }
        };
    }

    @Test
    void usesResolvedReferenceDateMinusThirtySixMonthsAndResolvesCurrencies() {
        AtomicReference<FetchContributionSummaryCommand> captured = new AtomicReference<>();
        ApimContributionSummaryPort port = command -> {
            captured.set(command);
            return Mono.just(new ContributionSummaryDataset("HKD", List.of(), List.of()));
        };

        var recordingPort = new RecordingCurrencyDisplayPort();
        ReferenceDatePort referenceDatePort = accountEnv -> Mono.just(LocalDate.of(2026, 3, 31));

        var service = new ExportContributionSummaryService(port, recordingPort, referenceDatePort,
                currentPortalAccessContextResolver());
        var result = service.execute(new ExportContributionSummaryCommand()).block();

        assertEquals("31/03/2023", captured.get().coverFrom());
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
        assertEquals("trustCode_for_contributions", result.trustCode());
        assertEquals("schemeType_for_contributions", result.schemeType());
        assertEquals("JP", result.accountEnv());
    }

    @Test
    void enrichesFetchCommandFromCurrentPortalAccessContext() {
        AtomicReference<FetchContributionSummaryCommand> captured = new AtomicReference<>();
        CurrencyDisplayPort currencyDisplayPort = (code, accountEnv, trustCode, schemeType) -> new CurrencyDisplay(code,
                code);

        var service = new ExportContributionSummaryService(
                command -> {
                    captured.set(command);
                    return Mono.just(new ContributionSummaryDataset("HKD", List.of(), List.of()));
                },
                currencyDisplayPort,
                () -> Mono.just(LocalDate.of(2026, 3, 31)),
                currentPortalAccessContextResolver());

        service.execute(new ExportContributionSummaryCommand()).block();

        assertEquals("policyNo_for_contributions", captured.get().policyNo());
        assertEquals("certNo_for_contributions", captured.get().certNo());
        assertEquals("userId_for_contributions", captured.get().userId());
        assertEquals("JP", captured.get().accountEnv());
        assertEquals("trustCode_for_contributions", captured.get().trustCode());
        assertEquals("schemeType_for_contributions", captured.get().schemeType());
    };

    private static final class RecordingCurrencyDisplayPort implements CurrencyDisplayPort {

        String capturedCode;
        String capturedAccountEnv;
        String capturedTrustCode;
        String capturedSchemeType;

        @Override
        public CurrencyDisplay resolveCurrencyDisplay(String code, String accountEnv, String trustCode,
                String schemeType) {
            this.capturedCode = code;
            this.capturedAccountEnv = accountEnv;
            this.capturedTrustCode = trustCode;
            this.capturedSchemeType = schemeType;
            return new CurrencyDisplay(code, "港元");
        }
    }
}
