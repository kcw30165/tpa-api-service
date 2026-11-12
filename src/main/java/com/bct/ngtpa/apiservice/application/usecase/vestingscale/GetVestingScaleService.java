package com.bct.ngtpa.apiservice.application.usecase.vestingscale;

import com.bct.ngtpa.apiservice.application.dto.vestingscale.GetVestingScaleCommand;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleCommand;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleResult;
import com.bct.ngtpa.apiservice.application.port.in.GetVestingScaleUseCase;
import com.bct.ngtpa.apiservice.application.port.out.VestingScalePort;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class GetVestingScaleService implements GetVestingScaleUseCase {

  private final VestingScalePort vestingScalePort;
  private final VestingScaleVisibilityPolicy visibilityPolicy;

  public GetVestingScaleService(
      VestingScalePort vestingScalePort, VestingScaleVisibilityPolicy visibilityPolicy) {
    this.vestingScalePort = Objects.requireNonNull(vestingScalePort);
    this.visibilityPolicy = Objects.requireNonNull(visibilityPolicy);
  }

  @Override
  public Mono<VestingScaleResult> get(GetVestingScaleCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    var portCommand =
        new VestingScaleCommand(
            command.accountEnv(),
            command.policyNo(),
            command.certNo(),
            command.actorUserId(),
            command.actorUserRole());
    return vestingScalePort.load(portCommand).map(visibilityPolicy::apply);
  }
}
