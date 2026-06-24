package com.bct.ngtpa.apiservice.config;

import com.bct.ngtpa.apiservice.application.port.in.ExportContributionSummaryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.GetContributionSummaryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.GetNotificationsUseCase;
import com.bct.ngtpa.apiservice.application.port.in.RefreshReferenceDateUseCase;
import com.bct.ngtpa.apiservice.application.port.in.CleanUpAllReferenceDatesUseCase;
import com.bct.ngtpa.apiservice.application.port.in.GetAllReferenceDatesUseCase;
import com.bct.ngtpa.apiservice.application.port.in.GetPersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.in.GetReferenceDataCountriesUseCase;
import com.bct.ngtpa.apiservice.application.port.in.UpdateNotificationsReadStatusUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDateRefreshPort;
import com.bct.ngtpa.apiservice.application.port.in.UpdatePersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimContributionSummaryPort;
import com.bct.ngtpa.apiservice.application.port.out.ApimMemberInfoPort;
import com.bct.ngtpa.apiservice.application.port.out.ApimNoticeMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.ApimNotificationReadStatusPort;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDataCountriesPort;
import com.bct.ngtpa.apiservice.application.port.out.ApimUpdatePersonalInformationPort;
import com.bct.ngtpa.apiservice.application.port.out.ContributionActionPermissionPort;
import com.bct.ngtpa.apiservice.application.port.out.CurrencyDisplayPort;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDateCacheUpdatePort;
import com.bct.ngtpa.apiservice.application.port.out.CacheAdminPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.usecase.ExportContributionSummaryService;
import com.bct.ngtpa.apiservice.application.usecase.GetContributionSummaryService;
import com.bct.ngtpa.apiservice.application.usecase.GetNotificationsService;
import com.bct.ngtpa.apiservice.application.usecase.RefreshReferenceDateService;
import com.bct.ngtpa.apiservice.application.usecase.CleanUpAllReferenceDatesService;
import com.bct.ngtpa.apiservice.application.usecase.GetAllReferenceDatesService;
import com.bct.ngtpa.apiservice.application.usecase.GetPersonalInformationService;
import com.bct.ngtpa.apiservice.application.usecase.GetReferenceDataCountriesService;
import com.bct.ngtpa.apiservice.application.usecase.UpdateNotificationsReadStatusService;
import org.springframework.beans.factory.annotation.Value;

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
                        CurrentPortalAccessContextResolver currentPortalAccessContextResolver,
                        ReferenceDatePort referenceDatePort) {
                return new GetNotificationsService(
                                apimNoticeMessagePort, currentPortalAccessContextResolver, referenceDatePort);
        }

        @Bean
        public UpdateNotificationsReadStatusUseCase updateNotificationsReadStatusUseCase(
                        ApimNotificationReadStatusPort apimNotificationReadStatusPort,
                        CurrentPortalAccessContextResolver currentPortalAccessContextResolver,
                        ReferenceDatePort referenceDatePort) {
                return new UpdateNotificationsReadStatusService(
                                apimNotificationReadStatusPort, currentPortalAccessContextResolver, referenceDatePort);
        }

        @Bean
        public GetContributionSummaryUseCase getContributionSummaryUseCase(
                        ApimContributionSummaryPort apimContributionSummaryPort,
                        CurrencyDisplayPort currencyDisplayPort,
                        ReferenceDatePort referenceDatePort,
                        CurrentPortalAccessContextResolver currentPortalAccessContextResolver,
                        ContributionActionPermissionPort contributionActionPermissionPort) {
                return new GetContributionSummaryService(
                                apimContributionSummaryPort, currencyDisplayPort, referenceDatePort,
                                currentPortalAccessContextResolver,
                                contributionActionPermissionPort);
        }

        @Bean
        public ExportContributionSummaryUseCase exportContributionSummaryUseCase(
                        ApimContributionSummaryPort apimContributionSummaryPort,
                        CurrencyDisplayPort currencyDisplayPort,
                        ReferenceDatePort referenceDatePort,
                        CurrentPortalAccessContextResolver currentPortalAccessContextResolver) {
                return new ExportContributionSummaryService(
                                apimContributionSummaryPort, currencyDisplayPort, referenceDatePort,
                                currentPortalAccessContextResolver);
        }

        @Bean
        public GetReferenceDataCountriesUseCase getReferenceDataCountriesUseCase(
                        ApimReferenceDataCountriesPort apimReferenceDataCountriesPort) {
                return new GetReferenceDataCountriesService(apimReferenceDataCountriesPort);
        }

        @Bean
        public GetPersonalInformationUseCase getPersonalInformationUseCase(
                        ApimMemberInfoPort apimMemberInfoPort,
                        CurrentPortalAccessContextResolver currentPortalAccessContextResolver) {
                return new GetPersonalInformationService(
                                apimMemberInfoPort,
                                currentPortalAccessContextResolver);
        }

        @Bean
        public UpdatePersonalInformationUseCase updatePersonalInformationUseCase(
                        ApimUpdatePersonalInformationPort apimUpdatePersonalInformationPort,
                        CurrentPortalAccessContextResolver currentPortalAccessContextResolver) {
                return new UpdatePersonalInformationService(
                                apimUpdatePersonalInformationPort,
                                currentPortalAccessContextResolver);
        }

        @Bean
        public RefreshReferenceDateUseCase refreshReferenceDateUseCase(
                        ApimReferenceDateRefreshPort apimReferenceDateRefreshPort,
                        ReferenceDateCacheUpdatePort referenceDateCacheUpdatePort,
                        @Value("${redis-cache.key-prefix:ngtpa}") String redisKeyPrefix) {
                return new RefreshReferenceDateService(
                                apimReferenceDateRefreshPort,
                                referenceDateCacheUpdatePort,
                                redisKeyPrefix);
        }

        @Bean
        public GetAllReferenceDatesUseCase getAllReferenceDatesUseCase(CacheAdminPort cacheAdminPort) {
                return new GetAllReferenceDatesService(cacheAdminPort);
        }

        @Bean
        public CleanUpAllReferenceDatesUseCase cleanUpAllReferenceDatesUseCase(CacheAdminPort cacheAdminPort) {
                return new CleanUpAllReferenceDatesService(cacheAdminPort);
        }
}
