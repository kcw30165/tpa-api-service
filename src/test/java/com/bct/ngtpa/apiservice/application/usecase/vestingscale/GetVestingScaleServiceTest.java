package com.bct.ngtpa.apiservice.application.usecase.vestingscale;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.GetVestingScaleCommand;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.Presence;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleCommand;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleConfigValue;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleData;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleMember;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScalePlan;
import com.bct.ngtpa.apiservice.application.dto.vestingscale.VestingScaleRow;
import com.bct.ngtpa.apiservice.application.port.out.VestingScalePort;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GetVestingScaleServiceTest {

  @Test
  void buildsPortCommandFromAllExplicitContextFieldsAndAppliesVisibility() {
    var command =
        new GetVestingScaleCommand(
            "env-a", "policy-1", "cert-2", "actor-3", ActorUserRole.ROLE_USER_MBR);
    var portCommand = new AtomicReference<VestingScaleCommand>();
    var port =
        (VestingScalePort)
            input -> {
              portCommand.set(input);
              return Mono.just(dataWithHiddenMemberCategory());
            };
    var service = new GetVestingScaleService(port, new VestingScaleVisibilityPolicy());

    StepVerifier.create(service.get(command))
        .assertNext(
            result -> {
              assertThat(result.member().memberCategory()).isEqualTo(Presence.absent());
              assertThat(result.vestingScaleSectionVisible()).isTrue();
            })
        .verifyComplete();

    assertThat(portCommand.get())
        .isEqualTo(
            new VestingScaleCommand(
                "env-a", "policy-1", "cert-2", "actor-3", ActorUserRole.ROLE_USER_MBR));
  }

  @Test
  void propagatesPortExceptionByIdentity() {
    var failure = new IllegalStateException("downstream failure");
    var port = (VestingScalePort) command -> Mono.error(failure);
    var service = new GetVestingScaleService(port, new VestingScaleVisibilityPolicy());

    StepVerifier.create(
            service.get(
                new GetVestingScaleCommand(
                    "env", "policy", "cert", "actor", ActorUserRole.ROLE_USER_MBR)))
        .expectErrorMatches(error -> error == failure)
        .verify();
  }

  private static VestingScaleData dataWithHiddenMemberCategory() {
    var member =
        new VestingScaleMember(
            Presence.present("member"), Presence.absent(), Presence.absent(), Presence.absent());
    var plan =
        new VestingScalePlan(
            "plan",
            "type-en",
            "type-chi",
            Presence.absent(),
            Presence.absent(),
            Presence.absent(),
            Presence.present("base-en"),
            Presence.present("base-chi"),
            Presence.absent(),
            Presence.absent(),
            Presence.absent(),
            Presence.absent(),
            Presence.present(List.of(new VestingScaleRow(1, new BigDecimal("25")))));
    return new VestingScaleData(
        member, List.of(plan), Map.of("member-category", VestingScaleConfigValue.HIDDEN));
  }
}
