package com.bct.ngtpa.apiservice.adapter.out.apim.login;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.dto.TrpMemberLoginApimDataItem;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TrpMemberLoginUnsuccessfulItemTest {
  @Test
  void blankErrorAndSingleUnsuccessfulItemMapsToIncorrectPassword() {
    var body = new ApimResponseBody<TrpMemberLoginApimDataItem>();
    body.setErrMessage("");
    body.setData(List.of(new TrpMemberLoginApimDataItem(false, null, 1, List.of(), null, null, null)));
    var envelope = new ApimResponseEnvelope<TrpMemberLoginApimDataItem>();
    envelope.setResponse(body);
    TrpMemberLoginTransport transport = command -> Mono.just(envelope);
    var adapter = new TrpMemberLoginAdapter(transport, new TrpMemberLoginContractMapper());

    StepVerifier.create(
            adapter.authenticate(
                new CredentialAuthenticationCommand(
                    "member",
                    "Wrong",
                    "en",
                    "req",
                    com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR)))
        .assertNext(
            result -> {
              assertThat(result.status())
                  .isEqualTo(CredentialAuthenticationStatus.INCORRECT_PASSWORD);
              assertThat(result.failCount()).isEqualTo(1);
              assertThat(result.downstreamCode()).isNull();
            })
        .verifyComplete();
  }
}
