package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateCommand;
import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateResult;
import com.bct.ngtpa.apiservice.application.port.in.RefreshReferenceDateUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDateRefreshPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.format.DateTimeFormatter;

@RequiredArgsConstructor
public class RefreshReferenceDateService implements RefreshReferenceDateUseCase {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ApimReferenceDateRefreshPort apimReferenceDateRefreshPort;

    @Override
    public Mono<RefreshReferenceDateResult> execute(RefreshReferenceDateCommand command) {
        return apimReferenceDateRefreshPort.fetchReferenceDate(command.accountEnv())
                .map(referenceDate -> new RefreshReferenceDateResult(
                        command.accountEnv(),
                        referenceDate.format(DATE_FORMATTER),
                        false,
                        false));
    }
}