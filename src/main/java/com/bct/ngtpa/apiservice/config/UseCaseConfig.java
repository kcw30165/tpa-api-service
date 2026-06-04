package com.bct.ngtpa.apiservice.config;

import com.bct.ngtpa.apiservice.application.port.in.ExportContributionSummaryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.GetContributionSummaryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.GetNotificationsUseCase;
import com.bct.ngtpa.apiservice.application.port.in.GetPersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.in.GetReferenceDataCountriesUseCase;
import com.bct.ngtpa.apiservice.application.port.in.UpdateNotificationsReadStatusUseCase;
import com.bct.ngtpa.apiservice.application.port.in.UpdatePersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimContributionSummaryPort;
import com.bct.ngtpa.apiservice.application.port.out.ApimMemberInfoPort;
import com.bct.ngtpa.apiservice.application.port.out.ApimNoticeMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.ApimNotificationReadStatusPort;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDataCountriesPort;
import com.bct.ngtpa.apiservice.application.port.out.ApimUpdatePersonalInformationPort;
import com.bct.ngtpa.apiservice.application.port.out.ContributionActionPermissionPort;
import com.bct.ngtpa.apiservice.application.port.out.CurrencyDisplayPort;
import com.bct.ngtpa.apiservice.application.port.out.PortalAccessContextPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.usecase.ExportContributionSummaryService;
import com.bct.ngtpa.apiservice.application.usecase.GetContributionSummaryService;
import com.bct.ngtpa.apiservice.application.usecase.GetNotificationsService;
import com.bct.ngtpa.apiservice.application.usecase.GetPersonalInformationService;
import com.bct.ngtpa.apiservice.application.usecase.GetReferenceDataCountriesService;
import com.bct.ngtpa.apiservice.application.usecase.UpdateNotificationsReadStatusService;
import com.bct.ngtpa.apiservice.application.usecase.UpdatePersonalInformationService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring composition configuration for application-layer use cases.
 *
 * <p>
 * Wires use case implementations as Spring beans without annotating the
 * implementation
 * classes themselves with {@code @Service}, keeping the application layer free
 * of
 * Spring Framework dependencies.
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public GetNotificationsUseCase getNotificationsUseCase(
            ApimNoticeMessagePort apimNoticeMessagePort,
            PortalAccessContextPort portalAccessContextPort,
            ReferenceDatePort referenceDatePort) {
        return new GetNotificationsService(apimNoticeMessagePort, portalAccessContextPort, referenceDatePort);
    }

    @Bean
    public UpdateNotificationsReadStatusUseCase updateNotificationsReadStatusUseCase(
            ApimNotificationReadStatusPort apimNotificationReadStatusPort,
            PortalAccessContextPort portalAccessContextPort,
            ReferenceDatePort referenceDatePort) {
        return new UpdateNotificationsReadStatusService(
                apimNotificationReadStatusPort, portalAccessContextPort, referenceDatePort);
    }

    @Bean
    public GetContributionSummaryUseCase getContributionSummaryUseCase(
            ApimContributionSummaryPort apimContributionSummaryPort,
            CurrencyDisplayPort currencyDisplayPort,
            ReferenceDatePort referenceDatePort,
            PortalAccessContextPort portalAccessContextPort,
            ContributionActionPermissionPort contributionActionPermissionPort) {
        return new GetContributionSummaryService(
                apimContributionSummaryPort, currencyDisplayPort, referenceDatePort, portalAccessContextPort,
                contributionActionPermissionPort);
    }

    @Bean
    public ExportContributionSummaryUseCase exportContributionSummaryUseCase(
            ApimContributionSummaryPort apimContributionSummaryPort,
            CurrencyDisplayPort currencyDisplayPort,
            ReferenceDatePort referenceDatePort,
            PortalAccessContextPort portalAccessContextPort) {
        return new ExportContributionSummaryService(
                apimContributionSummaryPort, currencyDisplayPort, referenceDatePort, portalAccessContextPort);
    }

    @Bean
    public GetReferenceDataCountriesUseCase getReferenceDataCountriesUseCase(
            ApimReferenceDataCountriesPort apimReferenceDataCountriesPort) {
        return new GetReferenceDataCountriesService(apimReferenceDataCountriesPort);
    }

    @Bean
    public GetPersonalInformationUseCase getPersonalInformationUseCase(
            ApimMemberInfoPort apimMemberInfoPort,
            PortalAccessContextPort portalAccessContextPort) {
        return new GetPersonalInformationService(
                apimMemberInfoPort,
                portalAccessContextPort);
    }

    @Bean
    public UpdatePersonalInformationUseCase updatePersonalInformationUseCase(
            ApimUpdatePersonalInformationPort apimUpdatePersonalInformationPort,
            PortalAccessContextPort portalAccessContextPort) {
        return new UpdatePersonalInformationService(apimUpdatePersonalInformationPort, portalAccessContextPort);
    }

}
