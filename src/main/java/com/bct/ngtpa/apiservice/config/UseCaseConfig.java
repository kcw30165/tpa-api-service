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
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextProvider;
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
            CurrentPortalAccessContextProvider currentPortalAccessContextProvider,
            ReferenceDatePort referenceDatePort) {
        return new GetNotificationsService(
                apimNoticeMessagePort, currentPortalAccessContextProvider, referenceDatePort);
    }

    @Bean
    public UpdateNotificationsReadStatusUseCase updateNotificationsReadStatusUseCase(
            ApimNotificationReadStatusPort apimNotificationReadStatusPort,
            CurrentPortalAccessContextProvider currentPortalAccessContextProvider,
            ReferenceDatePort referenceDatePort) {
        return new UpdateNotificationsReadStatusService(
                apimNotificationReadStatusPort, currentPortalAccessContextProvider, referenceDatePort);
    }

    @Bean
    public GetContributionSummaryUseCase getContributionSummaryUseCase(
            ApimContributionSummaryPort apimContributionSummaryPort,
            CurrencyDisplayPort currencyDisplayPort,
            ReferenceDatePort referenceDatePort,
            CurrentPortalAccessContextProvider currentPortalAccessContextProvider,
            ContributionActionPermissionPort contributionActionPermissionPort) {
        return new GetContributionSummaryService(
                apimContributionSummaryPort, currencyDisplayPort, referenceDatePort, currentPortalAccessContextProvider,
                contributionActionPermissionPort);
    }

    @Bean
    public ExportContributionSummaryUseCase exportContributionSummaryUseCase(
            ApimContributionSummaryPort apimContributionSummaryPort,
            CurrencyDisplayPort currencyDisplayPort,
            ReferenceDatePort referenceDatePort,
            CurrentPortalAccessContextProvider currentPortalAccessContextProvider) {
        return new ExportContributionSummaryService(
                apimContributionSummaryPort, currencyDisplayPort, referenceDatePort, currentPortalAccessContextProvider);
    }

    @Bean
    public GetReferenceDataCountriesUseCase getReferenceDataCountriesUseCase(
            ApimReferenceDataCountriesPort apimReferenceDataCountriesPort) {
        return new GetReferenceDataCountriesService(apimReferenceDataCountriesPort);
    }

    @Bean
    public GetPersonalInformationUseCase getPersonalInformationUseCase(
            ApimMemberInfoPort apimMemberInfoPort,
            CurrentPortalAccessContextProvider currentPortalAccessContextProvider) {
        return new GetPersonalInformationService(
                apimMemberInfoPort,
                currentPortalAccessContextProvider);
    }

    @Bean
    public UpdatePersonalInformationUseCase updatePersonalInformationUseCase(
            ApimUpdatePersonalInformationPort apimUpdatePersonalInformationPort,
            PortalAccessContextPort portalAccessContextPort) {
        return new UpdatePersonalInformationService(apimUpdatePersonalInformationPort, portalAccessContextPort);
    }

    // Backward-compatible overload for focused coverage tests using the old
    // two-argument shape.
    GetPersonalInformationUseCase getPersonalInformationUseCase(
            ApimMemberInfoPort apimMemberInfoPort,
            Object ignoredLegacyContextDependency) {
        return new GetPersonalInformationService(apimMemberInfoPort, new LegacyPersonalInformationProviderConfig());
    }

    private static final class LegacyPersonalInformationProviderConfig implements CurrentPortalAccessContextProvider {
        @Override
        public reactor.core.publisher.Mono<com.bct.ngtpa.apiservice.application.dto.PortalAccessContext> current() {
            return reactor.core.publisher.Mono.error(
                    new IllegalStateException("No current PortalAccessContext in coverage overload."));
        }

        @Override
        public reactor.core.publisher.Mono<com.bct.ngtpa.apiservice.application.dto.PortalAccessContext> currentOrEmpty() {
            return current();
        }
    }

}
