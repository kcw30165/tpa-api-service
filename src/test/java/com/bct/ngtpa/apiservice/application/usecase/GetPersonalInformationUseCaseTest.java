package com.bct.ngtpa.apiservice.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.FetchMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoResult;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.port.out.ApimMemberInfoPort;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextPort;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class GetPersonalInformationUseCaseTest {

    private static final PortalAccessContext SAMPLE_CONTEXT = new PortalAccessContext(
            new ActorContext("actor-user", "RM"),
            new AccountContext("acc-ref", "JP", "policy-111", "cert-222", "trustX", "schemeA", TermStatus.BLANK, null)
    );

    @Test
    void resolvesPortalContextAndCallsApimAndReturnsNeutralResult() {
        AtomicReference<String> capturedAccountRef = new AtomicReference<>();
        AtomicReference<FetchMemberInfoCommand> capturedFetchCommand = new AtomicReference<>();

        PortalAccessContextPort portalPort = accountRef -> {
            capturedAccountRef.set(accountRef);
            return Mono.just(SAMPLE_CONTEXT);
        };
        ApimMemberInfoPort apimPort = command -> {
            capturedFetchCommand.set(command);
            Map<String, Object> payload = new HashMap<>();
            payload.put("config", Map.of("addr1", "EDITABLE_COM"));
            payload.put("data", Map.of("addr1", "1 Example Street"));
            return Mono.just(new MemberInfoResult(payload));
        };

        var result = new GetPersonalInformationService(apimPort, portalPort)
                .execute(new GetPersonalInformationCommand("acc-ref", "en"))
                .block();

        assertEquals("acc-ref", capturedAccountRef.get());
        assertEquals("JP", capturedFetchCommand.get().getAccountEnv());
        assertEquals("policy-111", capturedFetchCommand.get().getPolicyNo());
        assertEquals("cert-222", capturedFetchCommand.get().getCertNo());
        assertEquals("actor-user", capturedFetchCommand.get().getUserId());
        assertEquals(Map.of("addr1", "1 Example Street"), result.data());
        assertEquals(Map.of("addr1", "EDITABLE_COM"), result.config());
    }

    @Test
    void propagatesApimExceptions() {
        ApimMemberInfoPort failingApim = command -> Mono.error(new IllegalStateException("apim-failure"));
        PortalAccessContextPort portalPort = accountRef -> Mono.just(SAMPLE_CONTEXT);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                new GetPersonalInformationService(failingApim, portalPort)
                        .execute(new GetPersonalInformationCommand("acc-ref", "en"))
                        .block());

        assertEquals("apim-failure", ex.getMessage());
    }
}
