package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.ExportContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.FetchContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.port.out.ApimContributionSummaryPort;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryDataset;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExportContributionSummaryServiceTest {

    @Test
    void usesDeterministicRefDateMinusThirtySixMonths() {
        AtomicReference<FetchContributionSummaryCommand> captured = new AtomicReference<>();
        ApimContributionSummaryPort port = command -> {
            captured.set(command);
            return Mono.just(new ContributionSummaryDataset("HKD", List.of(), List.of()));
        };

        var service = new ExportContributionSummaryService(port);
        service.execute(new ExportContributionSummaryCommand("JP", "MBR")).block();

        assertEquals("01/10/2022", captured.get().coverFrom());
        assertEquals("01/10/2025", captured.get().coverTo());
        assertEquals("policy-no", captured.get().policyNo());
        assertEquals("2", captured.get().certNo());
        assertEquals("user-id", captured.get().userId());
    }
}