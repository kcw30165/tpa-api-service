package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.FetchMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoResult;
import com.bct.ngtpa.apiservice.application.dto.MemberOwnerContext;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.port.out.ApimMemberInfoPort;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextPort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetPersonalInformationServiceTest {

    private final ApimMemberInfoPort apimMemberInfoPort = mock(ApimMemberInfoPort.class);
    private final PortalAccessContextPort portalAccessContextPort = mock(PortalAccessContextPort.class);
    private final GetPersonalInformationService service = new GetPersonalInformationService(
            apimMemberInfoPort,
            portalAccessContextPort);

    @Test
    void returnsNeutralPersonalInformationResultWithoutPageMapping() {
        var context = new PortalAccessContext(
                new ActorContext("user-1", "MEMBER", "SELF"),
                new MemberOwnerContext("user-1", "MBR"),
                new AccountContext("ACC-123", "JP", "policy-1", "cert-1", "JPM", "OE", TermStatus.BLANK, null));
        var payload = Map.<String, Object>of(
                "data", Map.of("addr1", "ABC Street", "email", "nick@example.com"),
                "config", Map.of("addr1", "EDITABLE_COM", "email", "READONLY"));

        when(portalAccessContextPort.resolvePortalAccessContext("ACC-123"))
                .thenReturn(Mono.just(context));
        when(apimMemberInfoPort.fetchMemberInfo(eq(new FetchMemberInfoCommand("JP", "policy-1", "cert-1", "user-1"))))
                .thenReturn(Mono.just(new MemberInfoResult(payload)));

        StepVerifier.create(service.execute(new GetPersonalInformationCommand("ACC-123", "en")))
                .expectNextMatches(result ->
                        "ABC Street".equals(result.data().get("addr1"))
                                && "nick@example.com".equals(result.data().get("email"))
                                && "EDITABLE_COM".equals(result.config().get("addr1"))
                                && "READONLY".equals(result.config().get("email")))
                .verifyComplete();

        verify(portalAccessContextPort).resolvePortalAccessContext("ACC-123");
    }
}
