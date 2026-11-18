package com.bct.ngtpa.apiservice.application.usecase.downloadcorner;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.downloadcorner.DownloadCornerCategory;
import com.bct.ngtpa.apiservice.application.dto.downloadcorner.DownloadCornerCommand;
import com.bct.ngtpa.apiservice.application.dto.downloadcorner.DownloadCornerData;
import com.bct.ngtpa.apiservice.application.dto.downloadcorner.DownloadCornerItem;
import com.bct.ngtpa.apiservice.application.dto.downloadcorner.GetDownloadCornerCommand;
import com.bct.ngtpa.apiservice.application.port.out.DownloadCornerPort;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GetDownloadCornerServiceTest {

  @Test
  void buildsPortCommandFromAllExplicitContextFieldsAndReturnsPortData() {
    var command =
        new GetDownloadCornerCommand(
            "env-a", "policy-1", "cert-2", "actor-3", ActorUserRole.ROLE_USER_MBR);
    var category =
        new DownloadCornerCategory(
            1,
            "Forms",
            "表格",
            List.of(
                new DownloadCornerItem(
                    2, "Form", "表格", "https://example.test/form.pdf", null, "form.pdf", null)));
    var data = new DownloadCornerData(List.of(category));
    var portCommand = new AtomicReference<DownloadCornerCommand>();
    DownloadCornerPort port =
        input -> {
          portCommand.set(input);
          return Mono.just(data);
        };
    var service = new GetDownloadCornerService(port);

    StepVerifier.create(service.get(command))
        .assertNext(result -> assertThat(result.categories()).containsExactly(category))
        .verifyComplete();

    assertThat(portCommand.get())
        .isEqualTo(
            new DownloadCornerCommand(
                "env-a", "policy-1", "cert-2", "actor-3", ActorUserRole.ROLE_USER_MBR));
    assertThat(command.toString()).isEqualTo("GetDownloadCornerCommand[<redacted>]");
  }

  @Test
  void propagatesPortExceptionByIdentity() {
    var failure = new IllegalStateException("downstream failure");
    DownloadCornerPort port = command -> Mono.error(failure);
    var service = new GetDownloadCornerService(port);

    StepVerifier.create(
            service.get(
                new GetDownloadCornerCommand(
                    "env", "policy", "cert", "actor", ActorUserRole.ROLE_USER_MBR)))
        .expectErrorMatches(error -> error == failure)
        .verify();
  }
}
