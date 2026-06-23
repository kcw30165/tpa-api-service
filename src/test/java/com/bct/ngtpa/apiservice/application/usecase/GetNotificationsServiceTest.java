package com.bct.ngtpa.apiservice.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.GetNotificationsCommand;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.port.out.ApimNoticeMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class GetNotificationsServiceTest {

    @Test
    void enrichesApimCommandFromCurrentPortalAccessContext() {
        AtomicReference<GetNotificationsCommand> captured = new AtomicReference<>();
        ApimNoticeMessagePort apimPort = command -> {
            captured.set(command);
            return Mono.just(new NotificationListResult(List.of()));
        };
        ReferenceDatePort referenceDatePort = accountEnv -> Mono.just(LocalDate.of(2026, 6, 16));

        new GetNotificationsService(apimPort, provider(context("ACC-123")), referenceDatePort)
                .execute(new GetNotificationsCommand(null, null, 1, 20, null, null, null, null, null, null))
                .block();

        assertEquals("JP", captured.get().accountEnv());
        assertEquals("policy-1", captured.get().policyNo());
        assertEquals("cert-1", captured.get().certNo());
        assertEquals("user-1", captured.get().userId());
        assertEquals("16/06/2026", captured.get().refDate());
        assertEquals(1, captured.get().page());
        assertEquals(20, captured.get().size());
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
