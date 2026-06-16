package com.bct.ngtpa.apiservice.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.FetchMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoResult;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.port.out.ApimMemberInfoPort;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextResolver;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class GetPersonalInformationUseCaseTest {

    @Test
    void resolvesPortalContextAndCallsApimAndReturnsNeutralResult() {
        AtomicReference<FetchMemberInfoCommand> captured = new AtomicReference<>();
        ApimMemberInfoPort apimPort = command -> {
            captured.set(command);
            return Mono.just(new MemberInfoResult(Map.of(
                    "data", Map.of("email", "a@b.test"),
                    "config", Map.of("email", "READONLY"))));
        };

        var result = new GetPersonalInformationService(apimPort, resolver(context("acc-ref")))
                .execute(new GetPersonalInformationCommand("en"))
                .block();

        assertEquals("JP", captured.get().getAccountEnv());
        assertEquals("policy-1", captured.get().getPolicyNo());
        assertEquals("cert-1", captured.get().getCertNo());
        assertEquals("user-1", captured.get().getUserId());
        assertEquals("a@b.test", result.data().get("email"));
        assertEquals("READONLY", result.config().get("email"));
    }

    @Test
    void nullPayloadReturnsEmptyResult() {
        var result = new GetPersonalInformationService(
                command -> Mono.just(new MemberInfoResult(null)),
                resolver(context("acc-ref")))
                .execute(new GetPersonalInformationCommand("en"))
                .block();

        assertTrue(result.data().isEmpty());
        assertTrue(result.config().isEmpty());
    }

    private static PortalAccessContextResolver resolver(PortalAccessContext context) {
        return new PortalAccessContextResolver() {
            @Override
            public Mono<PortalAccessContext> current() {
                return Mono.just(context);
            }

            @Override
            public Mono<PortalAccessContext> currentOrEmpty() {
                return Mono.just(context);
            }
        };
    }

    private static PortalAccessContext context(String accountRef) {
        return new PortalAccessContext(
                new ActorContext("user-1", "SELF"),
                new AccountContext(accountRef, "JP", "policy-1", "cert-1", "JPM", "OE", TermStatus.BLANK, null));
    }
}
