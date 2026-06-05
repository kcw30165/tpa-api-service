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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
                                .thenReturn(Mono.just(context("ACC-123")));
                when(apimMemberInfoPort
                                .fetchMemberInfo(eq(new FetchMemberInfoCommand("JP", "policy-1", "cert-1", "user-1"))))
                                .thenReturn(Mono.just(new MemberInfoResult(payload)));

                StepVerifier.create(service.execute(new GetPersonalInformationCommand("ACC-123", "en")))
                                .expectNextMatches(result -> "ABC Street".equals(result.data().get("addr1"))
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
                                                                                                "item-name",
                                                                                                "Email Contact",
                                                                                                "item-type", "DATA",
                                                                                                "function",
                                                                                                "INFO_UPDATE",
                                                                                                "sch-type", "Any",
                                                                                                "config-value",
                                                                                                "READONLY"),
                                                                                Map.of(
                                                                                                "item-id", "addr1",
                                                                                                "item-name",
                                                                                                "Residential Address 1",
                                                                                                "item-type", "DATA",
                                                                                                "function",
                                                                                                "INFO_UPDATE",
                                                                                                "sch-type", "Any",
                                                                                                "config-value",
                                                                                                "HIDDEN")),
                                                                "data", List.of(Map.of("email",
                                                                                "HGPQITD.XW.YQGPG@PTOJY.CLI"))))));

                when(portalAccessContextPort.resolvePortalAccessContext("ACC-123"))
                                .thenReturn(Mono.just(context("ACC-123")));
                when(apimMemberInfoPort
                                .fetchMemberInfo(eq(new FetchMemberInfoCommand("JP", "policy-1", "cert-1", "user-1"))))
                                .thenReturn(Mono.just(new MemberInfoResult(payload)));

                StepVerifier.create(service.execute(new GetPersonalInformationCommand("ACC-123", "en")))
                                .expectNextMatches(result -> "HGPQITD.XW.YQGPG@PTOJY.CLI"
                                                .equals(result.data().get("email"))
                                                && result.config().isEmpty()
                                                && result.configItems().get("email") != null
                                                && result.configItems().get("email")
                                                                .itemType() == MemberInfoConfigItemType.DATA
                                                && "READONLY".equals(result.configItems().get("email").configValue()))
                                .verifyComplete();
        }

        @Test
        void normalizesNestedPayloadDataListAndSkipsNonMapEntries() {
                Map<String, Object> payload = Map.of(
                                "data", List.of(
                                                "not-a-map",
                                                Map.of(
                                                                "data", List.of(
                                                                                Map.of("email", "first@example.test"),
                                                                                "ignored",
                                                                                Map.of("addr1", "1 Branch Street")),
                                                                "config", List.of(
                                                                                "ignored",
                                                                                configItem("email", "DATA", "READONLY"),
                                                                                configItem("   ", "DATA",
                                                                                                "EDITABLE_COM")))));

                StepVerifier.create(
                                serviceReturning(payload).execute(new GetPersonalInformationCommand("ACC-123", "en")))
                                .assertNext(result -> {
                                        assertThat(result.data()).containsEntry("email", "first@example.test")
                                                        .containsEntry("addr1", "1 Branch Street");
                                        assertThat(result.config()).isEmpty();
                                        assertThat(result.configItems()).containsOnlyKeys("email");
                                        assertThat(result.configItems().get("email").itemType())
                                                        .isEqualTo(MemberInfoConfigItemType.DATA);
                                        assertThat(result.configItems().get("email").configValue())
                                                        .isEqualTo("READONLY");
                                })
                                .verifyComplete();
        }

        @Test
        void rawResponseEnvelopeFallsBackToOriginalPayloadWhenDataHasNoMapItems() {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("response", Map.of("data", List.of("not-a-map")));
                payload.put("data", Map.of("email", "direct@example.test"));
                Map<Object, Object> config = new LinkedHashMap<>();
                config.put("email", "EDITABLE_COM");
                config.put(99, null);
                payload.put("config", config);

                StepVerifier.create(serviceReturning(payload)
                                .execute(new GetPersonalInformationCommand("ACC-123", "zh-HK")))
                                .assertNext(result -> {
                                        assertThat(result.data()).containsEntry("email", "direct@example.test");
                                        assertThat(result.config()).containsEntry("email", "EDITABLE_COM")
                                                        .containsEntry("99", "null");
                                        assertThat(result.configItems()).isEmpty();
                                })
                                .verifyComplete();
        }

        @Test
        void emptyOrNullMemberInfoPayloadProducesEmptyResult() {
                StepVerifier.create(serviceReturning(null).execute(new GetPersonalInformationCommand("ACC-123", "en")))
                                .assertNext(result -> {
                                        assertThat(result.data()).isEmpty();
                                        assertThat(result.config()).isEmpty();
                                        assertThat(result.configItems()).isEmpty();
                                })
                                .verifyComplete();
        }

        @Test
        void configItemStringFieldsAreTrimmedAndBlankValuesBecomeNull() {
                Map<String, Object> payload = Map.of(
                                "config", List.of(Map.of(
                                                "item-id", " email ",
                                                "item-name", "  Email  ",
                                                "item-type", " data ",
                                                "function", "   ",
                                                "sch-type", " Any ",
                                                "config-value", " READONLY ")));

                StepVerifier.create(
                                serviceReturning(payload).execute(new GetPersonalInformationCommand("ACC-123", "en")))
                                .assertNext(result -> {
                                        assertThat(result.configItems()).containsOnlyKeys("email");
                                        var item = result.configItems().get("email");
                                        assertThat(item.itemName()).isEqualTo("Email");
                                        assertThat(item.itemType()).isEqualTo(MemberInfoConfigItemType.DATA);
                                        assertThat(item.function()).isNull();
                                        assertThat(item.schType()).isEqualTo("Any");
                                        assertThat(item.configValue()).isEqualTo("READONLY");
                                })
                                .verifyComplete();
        }

        private GetPersonalInformationService serviceReturning(Map<String, Object> payload) {
                ApimMemberInfoPort apimPort = command -> Mono.just(new MemberInfoResult(payload));
                PortalAccessContextPort portalPort = accountRef -> Mono.just(context(accountRef));
                return new GetPersonalInformationService(apimPort, portalPort);
        }

        @Test
        void resolvesPortalContextBuildsFetchCommandAndMapsPayload() {
                AtomicReference<String> capturedAccountRef = new AtomicReference<>();
                AtomicReference<FetchMemberInfoCommand> capturedFetchCommand = new AtomicReference<>();

                PortalAccessContextPort portalPort = accountRef -> {
                        capturedAccountRef.set(accountRef);
                        return Mono.just(context("ACC-123"));
                };
                ApimMemberInfoPort apimPort = command -> {
                        capturedFetchCommand.set(command);
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("config", Map.of("addr1", "EDITABLE_COM", "email", "READONLY"));
                        payload.put("data", Map.of("addr1", "1 Example Street", "email", "a@b.test"));
                        return Mono.just(new MemberInfoResult(payload));
                };

                var result = new GetPersonalInformationService(apimPort, portalPort)
                                .execute(new GetPersonalInformationCommand("ACC-123", "zh_HK"))
                                .block();

                assertEquals("ACC-123", capturedAccountRef.get());
                assertEquals("JP", capturedFetchCommand.get().getAccountEnv());
                assertEquals("policy-1", capturedFetchCommand.get().getPolicyNo());
                assertEquals("cert-1", capturedFetchCommand.get().getCertNo());
                assertEquals("user-1", capturedFetchCommand.get().getUserId());
                assertEquals(Map.of("addr1", "1 Example Street", "email", "a@b.test"), result.data());
                assertEquals(Map.of("addr1", "EDITABLE_COM", "email", "READONLY"), result.config());
        }

        @Test
        void mapsNullMemberInfoResultToEmptyDataAndConfig() {
                var result = new GetPersonalInformationService(
                                command -> Mono.just(new MemberInfoResult(null)),
                                accountRef -> Mono.just(context("ACC-123")))
                                .execute(new GetPersonalInformationCommand("ACC-123", "en"))
                                .block();

                assertTrue(result.data().isEmpty());
                assertTrue(result.config().isEmpty());
        }

        @Test
        void ignoresNonMapPayloadSections() {
                var result = new GetPersonalInformationService(
                                command -> Mono.just(new MemberInfoResult(Map.of("config", "not-a-map", "data", 123))),
                                accountRef -> Mono.just(context("ACC-123")))
                                .execute(new GetPersonalInformationCommand("ACC-123", "en"))
                                .block();

                assertTrue(result.data().isEmpty());
                assertTrue(result.config().isEmpty());
        }

        private static Map<String, Object> configItem(String id, String itemType, String configValue) {
                return Map.of(
                                "item-id", id,
                                "item-name", "Name " + id,
                                "item-type", itemType,
                                "function", "INFO_UPDATE",
                                "sch-type", "Any",
                                "config-value", configValue);
        }

        private static PortalAccessContext context(String accountRef) {
                return new PortalAccessContext(
                                new ActorContext("user-1", "MEMBER", "SELF"),
                                new MemberOwnerContext("user-1", "MBR"),
                                new AccountContext(accountRef, "JP", "policy-1", "cert-1", "JPM", "OE",
                                                TermStatus.BLANK, null));
        }
}
