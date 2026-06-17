package com.bct.ngtpa.apiservice.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusResult;
import com.bct.ngtpa.apiservice.application.port.out.ApimNotificationReadStatusPort;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.domain.model.MessageStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class UpdateNotificationsReadStatusServiceTest {

    @Test
    void enrichesApimCommandFromCurrentPortalAccessContext() {
        AtomicReference<UpdateNotificationsReadStatusCommand> captured = new AtomicReference<>();
        ApimNotificationReadStatusPort apimPort = command -> {
            captured.set(command);
            return Mono.just(new UpdateNotificationsReadStatusResult(List.of()));
        };
        ReferenceDatePort referenceDatePort = () -> Mono.just(LocalDate.of(2026, 6, 16));

        new UpdateNotificationsReadStatusService(apimPort, provider(context("ACC-123")), referenceDatePort)
                .execute(new UpdateNotificationsReadStatusCommand(
                        null, null, List.of("N-1", "N-2"), null, null, null, null, null))
                .block();

        assertEquals("JP", captured.get().accountEnv());
        assertEquals("policy-1", captured.get().policyNo());
        assertEquals("cert-1", captured.get().certNo());
        assertEquals("user-1", captured.get().userId());
        assertEquals("16/06/2026", captured.get().refDate());
        assertEquals(List.of("N-1", "N-2"), captured.get().notificationIds());
        assertEquals(MessageStatus.READ, captured.get().targetStatus());
    }

    private static CurrentPortalAccessContextResolver provider(PortalAccessContext context) {
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
