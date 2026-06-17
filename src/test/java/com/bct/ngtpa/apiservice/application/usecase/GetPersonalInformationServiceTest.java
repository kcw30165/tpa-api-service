package com.bct.ngtpa.apiservice.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.FetchMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoConfigItemType;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoResult;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.port.out.ApimMemberInfoPort;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GetPersonalInformationServiceTest {

    private final ApimMemberInfoPort apimMemberInfoPort = mock(ApimMemberInfoPort.class);
    private final GetPersonalInformationService service = new GetPersonalInformationService(
            apimMemberInfoPort,
            resolver(context("ACC-123")));

    @Test
    void returnsNeutralPersonalInformationResultWithoutPageMapping() {
        var payload = Map.<String, Object>of(
                "data", Map.of("addr1", "ABC Street", "email", "nick@example.com"),
                "config", Map.of("addr1", "EDITABLE_COM", "email", "READONLY"));
        when(apimMemberInfoPort.fetchMemberInfo(eq(new FetchMemberInfoCommand("JP", "policy-1", "cert-1", "user-1"))))
                .thenReturn(Mono.just(new MemberInfoResult(payload)));

        StepVerifier.create(service.execute(new GetPersonalInformationCommand("en")))
                .expectNextMatches(result -> "ABC Street".equals(result.data().get("addr1"))
                        && "nick@example.com".equals(result.data().get("email"))
                        && "EDITABLE_COM".equals(result.config().get("addr1"))
                        && "READONLY".equals(result.config().get("email")))
                .verifyComplete();
    }

    @Test
    void extractsApimResponseEnvelopeWithConfigItemsAndDataList() {
        var payload = Map.<String, Object>of(
                "response", Map.of(
                        "err-message", "",
                        "data", List.of(Map.of(
                                "config", List.of(Map.of(
                                        "item-id", "email",
                                        "item-name", "Email Contact",
                                        "item-type", "DATA",
                                        "function", "INFO_UPDATE",
                                        "sch-type", "Any",
                                        "config-value", "READONLY")),
                                "data", List.of(Map.of("email", "HGPQITD.XW.YQGPG@PTOJY.CLI"))))));
        when(apimMemberInfoPort.fetchMemberInfo(eq(new FetchMemberInfoCommand("JP", "policy-1", "cert-1", "user-1"))))
                .thenReturn(Mono.just(new MemberInfoResult(payload)));

        StepVerifier.create(service.execute(new GetPersonalInformationCommand("en")))
                .assertNext(result -> {
                    assertEquals("HGPQITD.XW.YQGPG@PTOJY.CLI", result.data().get("email"));
                    assertTrue(result.config().isEmpty());
                    assertThat(result.configItems()).containsOnlyKeys("email");
                    assertEquals(MemberInfoConfigItemType.DATA, result.configItems().get("email").itemType());
                    assertEquals("READONLY", result.configItems().get("email").configValue());
                })
                .verifyComplete();
    }

    @Test
    void resolvesPortalContextBuildsFetchCommandAndMapsPayload() {
        AtomicReference<FetchMemberInfoCommand> capturedFetchCommand = new AtomicReference<>();
        ApimMemberInfoPort apimPort = command -> {
            capturedFetchCommand.set(command);
            return Mono.just(new MemberInfoResult(Map.of(
                    "config", Map.of("addr1", "EDITABLE_COM", "email", "READONLY"),
                    "data", Map.of("addr1", "1 Example Street", "email", "a@b.test"))));
        };

        var result = new GetPersonalInformationService(apimPort, resolver(context("ACC-123")))
                .execute(new GetPersonalInformationCommand("zh_HK"))
                .block();

        assertEquals("JP", capturedFetchCommand.get().getAccountEnv());
        assertEquals("policy-1", capturedFetchCommand.get().getPolicyNo());
        assertEquals("cert-1", capturedFetchCommand.get().getCertNo());
        assertEquals("user-1", capturedFetchCommand.get().getUserId());
        assertEquals(Map.of("addr1", "1 Example Street", "email", "a@b.test"), result.data());
        assertEquals(Map.of("addr1", "EDITABLE_COM", "email", "READONLY"), result.config());
    }

    @Test
    void emptyOrNullMemberInfoPayloadProducesEmptyResult() {
        var result = new GetPersonalInformationService(
                command -> Mono.just(new MemberInfoResult(null)),
                resolver(context("ACC-123")))
                .execute(new GetPersonalInformationCommand("en"))
                .block();

        assertTrue(result.data().isEmpty());
        assertTrue(result.config().isEmpty());
        assertTrue(result.configItems().isEmpty());
    }

    private static CurrentPortalAccessContextResolver resolver(PortalAccessContext context) {
        return new CurrentPortalAccessContextResolver() {
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
