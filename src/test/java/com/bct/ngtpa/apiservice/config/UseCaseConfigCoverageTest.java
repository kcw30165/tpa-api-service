package com.bct.ngtpa.apiservice.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ContributionActions;
import com.bct.ngtpa.apiservice.application.dto.CurrencyDisplay;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoResult;
import com.bct.ngtpa.apiservice.application.dto.MemberOwnerContext;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDataCountryItem;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.dto.UpdateNotificationsReadStatusResult;
import com.bct.ngtpa.apiservice.application.port.out.ApimContributionSummaryPort;
import com.bct.ngtpa.apiservice.application.port.out.ApimMemberInfoPort;
import com.bct.ngtpa.apiservice.application.port.out.ApimNoticeMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.ApimNotificationReadStatusPort;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDataCountriesPort;
import com.bct.ngtpa.apiservice.application.port.out.ContributionActionPermissionPort;
import com.bct.ngtpa.apiservice.application.port.out.CurrencyDisplayPort;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.domain.model.ContributionSummaryDataset;
import com.bct.ngtpa.apiservice.application.dto.NotificationListResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class UseCaseConfigCoverageTest {

    @Test
    void createsAllUseCaseBeans() {
        UseCaseConfig config = new UseCaseConfig();
        PortalAccessContextPort portalContextPort = accountRef -> Mono.just(context());
        ReferenceDatePort referenceDatePort = () -> Mono.just(LocalDate.of(2026, 3, 31));
        ApimNoticeMessagePort noticePort = command -> Mono.just(new NotificationListResult(List.of()));
        ApimNotificationReadStatusPort readStatusPort = command -> Mono.just(new UpdateNotificationsReadStatusResult(List.of()));
        ApimContributionSummaryPort contributionPort = command -> Mono.just(new ContributionSummaryDataset("HKD", List.of(), List.of()));
        CurrencyDisplayPort currencyDisplayPort = (code, accountEnv, trustCode, schemeType) -> new CurrencyDisplay(code, code);
        ContributionActionPermissionPort actionPermissionPort = () -> new ContributionActions(true);
        ApimReferenceDataCountriesPort countriesPort = () -> Mono.just(List.of(new ReferenceDataCountryItem("HKG", "Hong Kong", "香港", "852")));
        ApimMemberInfoPort memberInfoPort = command -> Mono.just(new MemberInfoResult(Map.of()));

        assertNotNull(config.getNotificationsUseCase(noticePort, portalContextPort, referenceDatePort));
        assertNotNull(config.updateNotificationsReadStatusUseCase(readStatusPort, portalContextPort, referenceDatePort));
        assertNotNull(config.getContributionSummaryUseCase(
                contributionPort, currencyDisplayPort, referenceDatePort, portalContextPort, actionPermissionPort));
        assertNotNull(config.exportContributionSummaryUseCase(
                contributionPort, currencyDisplayPort, referenceDatePort, portalContextPort));
        assertNotNull(config.getReferenceDataCountriesUseCase(countriesPort));
        assertNotNull(config.getPersonalInformationUseCase(
                memberInfoPort,
                portalContextPort,
                (apimData, apimConfig, bffPagesProperties, language) -> Map.of(),
                new BffPagesProperties()));
    }

    private static PortalAccessContext context() {
        return new PortalAccessContext(
                new ActorContext("actor-user", "MEMBER", "SELF"),
                new MemberOwnerContext("owner-user", "MBR"),
                new AccountContext("ACC-123", "JP", "POL", "CERT", "TRUST", "SCHEME", TermStatus.BLANK, null));
    }
}
