package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.FetchFundFactSheetCommand;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.RawFundFactSheetData;
import reactor.core.publisher.Mono;

/** Outbound port that supplies the raw fund fact sheet rows of a policy. */
public interface FundFactSheetSourcePort {

  Mono<RawFundFactSheetData> fetch(FetchFundFactSheetCommand command);
}
