package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.GetContributionSummaryCommand;
import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import com.bct.ngtpa.apiservice.application.port.out.ApimContributionSummaryPort;
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
    void validatesDatesEnrichesCommandAndBuildsReport() {
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

        var service = new GetContributionSummaryService(port);
        var result = service.execute(new GetContributionSummaryCommand("JP", "MBR", "05/04/2026", "05/05/2026")).block();

        assertEquals("05/04/2026", captured.get().coverFrom());
        assertEquals("05/05/2026", captured.get().coverTo());
        assertEquals("policy-no", captured.get().policyNo());
        assertEquals("2", captured.get().certNo());
        assertEquals("user-id", captured.get().userId());
        assertEquals("", captured.get().trustCode());
        assertEquals("", captured.get().schemeType());
        assertEquals("HKD", result.currencyDisplay());
        assertEquals("HKD24908.45", com.bct.ngtpa.apiservice.adapter.in.web.response.ContributionSummaryResponse
                .from(result, new com.bct.ngtpa.apiservice.config.ContributionSummaryProperties())
                .contributions().getFirst().totalContribution());
    }

    @Test
    void rejectsMissingOrInvalidDates() {
        ApimContributionSummaryPort port = command -> Mono.just(new ContributionSummaryDataset("", List.of(), List.of()));
        var service = new GetContributionSummaryService(port);

        assertThrows(InvalidContributionRequestException.class,
                () -> service.execute(new GetContributionSummaryCommand("JP", "MBR", null, "05/05/2026")).block());
        assertThrows(InvalidContributionRequestException.class,
                () -> service.execute(new GetContributionSummaryCommand("JP", "MBR", "05/04/2026", "2026-05-05")).block());
    }
}