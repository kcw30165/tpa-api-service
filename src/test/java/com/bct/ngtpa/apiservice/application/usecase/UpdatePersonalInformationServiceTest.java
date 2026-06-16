package com.bct.ngtpa.apiservice.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.dto.UpdateMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import com.bct.ngtpa.apiservice.application.port.out.ApimUpdatePersonalInformationPort;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class UpdatePersonalInformationServiceTest {

    @Test
    void resolvesPortalContextAndCallsApimUpdateWithActorUserIdUserTypeAndMappedFields() {
        AtomicReference<UpdateMemberInfoCommand> capturedCommand = new AtomicReference<>();
        List<UpdatePersonalInformationResult> expectedResults = List.of(new UpdatePersonalInformationResult(
                true,
                true,
                "POL-001",
                "CERT-001",
                "JP",
                "990000001",
                "2026-06-05",
                "13:44:01",
                List.of()));

        ApimUpdatePersonalInformationPort apimPort = command -> {
            capturedCommand.set(command);
            return Mono.just(expectedResults);
        };

        var updateFields = new LinkedHashMap<String, Object>();
        updateFields.put("addr2", "Tai Po, New Territories");
        updateFields.put("mobile-number", "98765432");

        var result = new UpdatePersonalInformationService(apimPort, provider(context()))
                .execute(new UpdatePersonalInformationCommand("ACC-123", true, updateFields))
                .block();

        assertEquals(expectedResults, result);
        assertEquals("JP", capturedCommand.get().accountEnv());
        assertEquals("POL-001", capturedCommand.get().policyNo());
        assertEquals("CERT-001", capturedCommand.get().certNo());
        assertEquals("actor-user", capturedCommand.get().userId());
        assertEquals("MEMBER", capturedCommand.get().userRole());
        assertEquals(updateFields, capturedCommand.get().updateFields());
    }

    private static PortalAccessContext context() {
        return new PortalAccessContext(
                new ActorContext("actor-user", "MEMBER"),
                new AccountContext(
                        "ACC-123",
                        "JP",
                        "POL-001",
                        "CERT-001",
                        "JPM",
                        "OE",
                        TermStatus.BLANK,
                        null));
    }
        private static CurrentPortalAccessContextProvider provider(PortalAccessContext context) {
        return new CurrentPortalAccessContextProvider() {
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
}
