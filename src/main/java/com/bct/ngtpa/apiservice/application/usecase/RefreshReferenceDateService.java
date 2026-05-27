package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateCommand;
import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateResult;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDateConfigUpsertCommand;
import com.bct.ngtpa.apiservice.application.port.in.RefreshReferenceDateUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDateRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateConfigPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.format.DateTimeFormatter;

@RequiredArgsConstructor
public class RefreshReferenceDateService implements RefreshReferenceDateUseCase {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String CONFIG_KEY_PREFIX = "reference-date.";

    private final ApimReferenceDateRefreshPort apimReferenceDateRefreshPort;
    private final ReferenceDateConfigPort referenceDateConfigPort;

    @Override
    public Mono<RefreshReferenceDateResult> execute(RefreshReferenceDateCommand command) {
        return apimReferenceDateRefreshPort.fetchReferenceDate(command.accountEnv())
        .flatMap(referenceDate -> {
            String formattedReferenceDate = referenceDate.format(DATE_FORMATTER);
            var upsertCommand = new ReferenceDateConfigUpsertCommand(
                CONFIG_KEY_PREFIX + command.accountEnv(),
                formattedReferenceDate);
            return referenceDateConfigPort.upsertReferenceDate(upsertCommand)
                .thenReturn(new RefreshReferenceDateResult(
                    command.accountEnv(),
                    formattedReferenceDate,
                    true,
                    false));
        });
    }
}