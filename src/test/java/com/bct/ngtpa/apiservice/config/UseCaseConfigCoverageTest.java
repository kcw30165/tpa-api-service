package com.bct.ngtpa.apiservice.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.application.port.out.ApimContributionSummaryPort;
import com.bct.ngtpa.apiservice.application.port.out.ApimMemberInfoPort;
import com.bct.ngtpa.apiservice.application.port.out.ApimNoticeMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.ApimNotificationReadStatusPort;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDataCountriesPort;
import com.bct.ngtpa.apiservice.application.port.out.ContributionActionPermissionPort;
import com.bct.ngtpa.apiservice.application.port.out.CurrencyDisplayPort;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import org.junit.jupiter.api.Test;

class UseCaseConfigCoverageTest {

    @Test
    void createsAllUseCaseBeansWithMockedPorts() {
        UseCaseConfig config = new UseCaseConfig();

        ApimNoticeMessagePort noticePort = mock(ApimNoticeMessagePort.class);
        ApimNotificationReadStatusPort readStatusPort = mock(ApimNotificationReadStatusPort.class);
        ApimContributionSummaryPort contributionPort = mock(ApimContributionSummaryPort.class);
        ApimReferenceDataCountriesPort countriesPort = mock(ApimReferenceDataCountriesPort.class);
        ApimMemberInfoPort memberInfoPort = mock(ApimMemberInfoPort.class);
        CurrencyDisplayPort currencyDisplayPort = mock(CurrencyDisplayPort.class);
        CurrentPortalAccessContextResolver currentPortalAccessContextResolver = mock(CurrentPortalAccessContextResolver.class);
        ReferenceDatePort referenceDatePort = mock(ReferenceDatePort.class);
        ContributionActionPermissionPort actionPermissionPort = mock(ContributionActionPermissionPort.class);

        assertNotNull(config.getNotificationsUseCase(noticePort, currentPortalAccessContextResolver, referenceDatePort));
        assertNotNull(config.updateNotificationsReadStatusUseCase(readStatusPort, currentPortalAccessContextResolver, referenceDatePort));
        assertNotNull(config.getContributionSummaryUseCase(
                contributionPort,
                currencyDisplayPort,
                referenceDatePort,
                currentPortalAccessContextResolver,
                actionPermissionPort));
        assertNotNull(config.exportContributionSummaryUseCase(
                contributionPort,
                currencyDisplayPort,
                referenceDatePort,
                currentPortalAccessContextResolver));
        assertNotNull(config.getReferenceDataCountriesUseCase(countriesPort));
        assertNotNull(config.getPersonalInformationUseCase(memberInfoPort, currentPortalAccessContextResolver));
    }
}
