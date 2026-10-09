package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.FundFactSheetResult;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.GetFundFactSheetCommand;
import reactor.core.publisher.Mono;

public interface GetFundFactSheetUseCase {

  Mono<FundFactSheetResult> get(GetFundFactSheetCommand command);
}
