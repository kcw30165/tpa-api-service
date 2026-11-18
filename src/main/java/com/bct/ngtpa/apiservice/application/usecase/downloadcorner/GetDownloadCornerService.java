package com.bct.ngtpa.apiservice.application.usecase.downloadcorner;

import com.bct.ngtpa.apiservice.application.dto.downloadcorner.DownloadCornerCommand;
import com.bct.ngtpa.apiservice.application.dto.downloadcorner.DownloadCornerResult;
import com.bct.ngtpa.apiservice.application.dto.downloadcorner.GetDownloadCornerCommand;
import com.bct.ngtpa.apiservice.application.port.in.GetDownloadCornerUseCase;
import com.bct.ngtpa.apiservice.application.port.out.DownloadCornerPort;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class GetDownloadCornerService implements GetDownloadCornerUseCase {

  private final DownloadCornerPort downloadCornerPort;

  public GetDownloadCornerService(DownloadCornerPort downloadCornerPort) {
    this.downloadCornerPort = Objects.requireNonNull(downloadCornerPort);
  }

  @Override
  public Mono<DownloadCornerResult> get(GetDownloadCornerCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    var portCommand =
        new DownloadCornerCommand(
            command.accountEnv(),
            command.policyNo(),
            command.certNo(),
            command.actorUserId(),
            command.actorUserRole());
    return downloadCornerPort
        .load(portCommand)
        .map(data -> new DownloadCornerResult(data.categories()));
  }
}
