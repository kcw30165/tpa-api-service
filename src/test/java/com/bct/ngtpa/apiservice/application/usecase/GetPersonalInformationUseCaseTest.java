package com.bct.ngtpa.apiservice.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.FetchMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoResult;
import com.bct.ngtpa.apiservice.application.dto.MemberOwnerContext;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.port.out.ApimMemberInfoPort;
import com.bct.ngtpa.apiservice.application.port.out.PersonalInformationPageMapperPort;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextPort;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class GetPersonalInformationUseCaseTest {

    private static final PortalAccessContext SAMPLE_CONTEXT = new PortalAccessContext(
            new ActorContext("actor-user", "STAFF", "RM"),
            new MemberOwnerContext("member-123", "MBR"),
            new AccountContext("acc-ref", "JP", "policy-111", "cert-222", "trustX", "schemeA", TermStatus.BLANK, null)
    );

    @Test
    void resolves_portal_context_and_calls_apim_and_mapper_and_returns_page() {
        AtomicReference<String> capturedAccountRef = new AtomicReference<>();
        AtomicReference<FetchMemberInfoCommand> capturedFetchCommand = new AtomicReference<>();
        AtomicReference<Map<String, Object>> capturedApimData = new AtomicReference<>();
        AtomicReference<Map<String, String>> capturedApimConfig = new AtomicReference<>();
        AtomicReference<String> capturedLanguage = new AtomicReference<>();

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

        PersonalInformationPageMapperPort mapperPort = (apimData, apimConfig, language) -> {
            capturedApimData.set(apimData);
            capturedApimConfig.set(apimConfig);
            capturedLanguage.set(language);
            return Map.of(
                    "fields", Map.of("addr1", Map.of("value", "1 Example Street")),
                    "sections", Map.of(),
                    "confirmation", Map.of("enabled", false),
                    "language", language);
        };

        Map<String, Object> result = new GetPersonalInformationService(apimPort, portalPort, mapperPort)
                .execute(new GetPersonalInformationCommand("acc-ref", "en"))
                .block();

        assertEquals("acc-ref", capturedAccountRef.get());
        assertEquals("JP", capturedFetchCommand.get().getAccountEnv());
        assertEquals("policy-111", capturedFetchCommand.get().getPolicyNo());
        assertEquals("cert-222", capturedFetchCommand.get().getCertNo());
        assertEquals("actor-user", capturedFetchCommand.get().getUserId());
        assertEquals(Map.of("addr1", "1 Example Street"), capturedApimData.get());
        assertEquals(Map.of("addr1", "EDITABLE_COM"), capturedApimConfig.get());
        assertEquals("en", capturedLanguage.get());
        assertEquals("en", result.get("language"));
    }

    @Test
    void propagates_apim_exceptions() {
        ApimMemberInfoPort failingApim = command -> Mono.error(new IllegalStateException("apim-failure"));
        PortalAccessContextPort portalPort = accountRef -> Mono.just(SAMPLE_CONTEXT);
        PersonalInformationPageMapperPort mapperPort = (apimData, apimConfig, language) -> Map.of();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                new GetPersonalInformationService(failingApim, portalPort, mapperPort)
                        .execute(new GetPersonalInformationCommand("acc-ref", "en"))
                        .block());

        assertEquals("apim-failure", ex.getMessage());
    }
}