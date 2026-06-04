package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.FetchMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoConfigItemType;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoResult;
import com.bct.ngtpa.apiservice.application.dto.MemberOwnerContext;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.port.out.ApimMemberInfoPort;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextPort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
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
        var payload = Map.<String, Object>of(
                "data", Map.of("addr1", "ABC Street", "email", "nick@example.com"),
                "config", Map.of("addr1", "EDITABLE_COM", "email", "READONLY"));

        when(portalAccessContextPort.resolvePortalAccessContext("ACC-123"))
                .thenReturn(Mono.just(context()));
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

    @Test
    void extractsApimResponseEnvelopeWithConfigItemsAndDataList() {
        var payload = Map.<String, Object>of(
                "response", Map.of(
                        "err-message", "",
                        "data", List.of(Map.of(
                                "config", List.of(
                                        Map.of(
                                                "item-id", "email",
                                                "item-name", "Email Contact",
                                                "item-type", "DATA",
                                                "function", "INFO_UPDATE",
                                                "sch-type", "Any",
                                                "config-value", "READONLY"),
                                        Map.of(
                                                "item-id", "addr1",
                                                "item-name", "Residential Address 1",
                                                "item-type", "DATA",
                                                "function", "INFO_UPDATE",
                                                "sch-type", "Any",
                                                "config-value", "HIDDEN")),
                                "data", List.of(Map.of("email", "HGPQITD.XW.YQGPG@PTOJY.CLI"))))));

        when(portalAccessContextPort.resolvePortalAccessContext("ACC-123"))
                .thenReturn(Mono.just(context()));
        when(apimMemberInfoPort.fetchMemberInfo(eq(new FetchMemberInfoCommand("JP", "policy-1", "cert-1", "user-1"))))
                .thenReturn(Mono.just(new MemberInfoResult(payload)));

        StepVerifier.create(service.execute(new GetPersonalInformationCommand("ACC-123", "en")))
                .expectNextMatches(result ->
                        "HGPQITD.XW.YQGPG@PTOJY.CLI".equals(result.data().get("email"))
                                && result.config().isEmpty()
                                && result.configItems().get("email") != null
                                && result.configItems().get("email").itemType() == MemberInfoConfigItemType.DATA
                                && "READONLY".equals(result.configItems().get("email").configValue()))
                .verifyComplete();
    }

    private PortalAccessContext context() {
        return new PortalAccessContext(
                new ActorContext("user-1", "MEMBER", "SELF"),
                new MemberOwnerContext("user-1", "MBR"),
                new AccountContext("ACC-123", "JP", "policy-1", "cert-1", "JPM", "OE", TermStatus.BLANK, null));
    }
}
