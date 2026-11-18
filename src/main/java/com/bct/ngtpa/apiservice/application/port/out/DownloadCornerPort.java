package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.downloadcorner.DownloadCornerCommand;
import com.bct.ngtpa.apiservice.application.dto.downloadcorner.DownloadCornerData;
import reactor.core.publisher.Mono;

public interface DownloadCornerPort {
  Mono<DownloadCornerData> load(DownloadCornerCommand command);
}
