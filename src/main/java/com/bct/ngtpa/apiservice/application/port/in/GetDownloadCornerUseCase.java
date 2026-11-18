package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.downloadcorner.DownloadCornerResult;
import com.bct.ngtpa.apiservice.application.dto.downloadcorner.GetDownloadCornerCommand;
import reactor.core.publisher.Mono;

public interface GetDownloadCornerUseCase {
  Mono<DownloadCornerResult> get(GetDownloadCornerCommand command);
}
