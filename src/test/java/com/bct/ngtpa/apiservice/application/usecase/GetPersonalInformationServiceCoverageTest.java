package com.bct.ngtpa.apiservice.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PersonalInformationFieldMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class GetPersonalInformationServiceCoverageTest {

    @Test
    void resolvesPortalContextBuildsFetchCommandAndMapsPayload() {
        AtomicReference<String> capturedAccountRef = new AtomicReference<>();
        AtomicReference<FetchMemberInfoCommand> capturedFetchCommand = new AtomicReference<>();
        AtomicReference<Map<String, Object>> capturedData = new AtomicReference<>();
        AtomicReference<Map<String, String>> capturedConfig = new AtomicReference<>();
        AtomicReference<BffPagesProperties> capturedPages = new AtomicReference<>();
        AtomicReference<String> capturedLanguage = new AtomicReference<>();

        PortalAccessContextPort portalPort = accountRef -> {
            capturedAccountRef.set(accountRef);
            return Mono.just(context());
        };
        ApimMemberInfoPort apimPort = command -> {
            capturedFetchCommand.set(command);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("config", Map.of("addr1", "EDITABLE_COM", "email", "READONLY"));
            payload.put("data", Map.of("addr1", "1 Example Street", "email", "a@b.test"));
            return Mono.just(new MemberInfoResult(payload));
        };
        BffPagesProperties pages = new BffPagesProperties();
        PersonalInformationFieldMapper mapper = (apimData, apimConfig, bffPagesProperties, language) -> {
            capturedData.set(apimData);
            capturedConfig.set(apimConfig);
            capturedPages.set(bffPagesProperties);
            capturedLanguage.set(language);
            return Map.of("mapped", true);
        };

        Map<String, Object> result = new GetPersonalInformationService(apimPort, portalPort, mapper, pages)
                .execute(new GetPersonalInformationCommand("ACC-123", "zh_HK"))
                .block();

        assertEquals(Map.of("mapped", true), result);
        assertEquals("ACC-123", capturedAccountRef.get());
        assertEquals("JP", capturedFetchCommand.get().getAccountEnv());
        assertEquals("POL-001", capturedFetchCommand.get().getPolicyNo());
        assertEquals("CERT-001", capturedFetchCommand.get().getCertNo());
        assertEquals("actor-user", capturedFetchCommand.get().getUserId());
        assertEquals(Map.of("addr1", "1 Example Street", "email", "a@b.test"), capturedData.get());
        assertEquals(Map.of("addr1", "EDITABLE_COM", "email", "READONLY"), capturedConfig.get());
        assertSame(pages, capturedPages.get());
        assertEquals("zh_HK", capturedLanguage.get());
    }

    @Test
    void mapsNullMemberInfoResultToEmptyDataAndConfig() {
        PersonalInformationFieldMapper mapper = (apimData, apimConfig, bffPagesProperties, language) -> {
            assertTrue(apimData.isEmpty());
            assertTrue(apimConfig.isEmpty());
            assertEquals("en", language);
            return Map.of("empty", true);
        };

        Map<String, Object> result = new GetPersonalInformationService(
                command -> Mono.just(new MemberInfoResult(null)),
                accountRef -> Mono.just(context()),
                mapper,
                new BffPagesProperties())
                .execute(new GetPersonalInformationCommand("ACC-123", "en"))
                .block();

        assertEquals(Map.of("empty", true), result);
    }

    @Test
    void ignoresNonMapPayloadSections() {
        PersonalInformationFieldMapper mapper = (apimData, apimConfig, bffPagesProperties, language) -> {
            assertTrue(apimData.isEmpty());
            assertTrue(apimConfig.isEmpty());
            return Map.of("nonMap", true);
        };

        Map<String, Object> result = new GetPersonalInformationService(
                command -> Mono.just(new MemberInfoResult(Map.of("config", "not-a-map", "data", 123))),
                accountRef -> Mono.just(context()),
                mapper,
                new BffPagesProperties())
                .execute(new GetPersonalInformationCommand("ACC-123", "en"))
                .block();

        assertEquals(Map.of("nonMap", true), result);
    }

    private static PortalAccessContext context() {
        return new PortalAccessContext(
                new ActorContext("actor-user", "MEMBER", "SELF"),
                new MemberOwnerContext("owner-user", "MBR"),
                new AccountContext("ACC-123", "JP", "POL-001", "CERT-001", "TRUST", "SCHEME", TermStatus.BLANK, null));
    }
}
