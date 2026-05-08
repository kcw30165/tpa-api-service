package com.bct.ngtpa.apiservice.config;

import com.bct.ngtpa.apiservice.application.port.in.ExportContributionSummaryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.GetContributionSummaryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.GetNotificationsUseCase;
import com.bct.ngtpa.apiservice.application.port.in.UpdateNotificationsReadStatusUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimContributionSummaryPort;
import com.bct.ngtpa.apiservice.application.port.out.ApimNoticeMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.ApimNotificationReadStatusPort;
import com.bct.ngtpa.apiservice.application.port.out.CurrencyDisplayPort;
import com.bct.ngtpa.apiservice.application.port.out.MemberContextPort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.usecase.ExportContributionSummaryService;
import com.bct.ngtpa.apiservice.application.usecase.GetContributionSummaryService;
import com.bct.ngtpa.apiservice.application.usecase.GetNotificationsService;
import com.bct.ngtpa.apiservice.application.usecase.UpdateNotificationsReadStatusService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring composition configuration for application-layer use cases.
 *
 * <p>Wires use case implementations as Spring beans without annotating the implementation
 * classes themselves with {@code @Service}, keeping the application layer free of
 * Spring Framework dependencies.
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public GetNotificationsUseCase getNotificationsUseCase(
            ApimNoticeMessagePort apimNoticeMessagePort,
            MemberContextPort memberContextPort,
            ReferenceDatePort referenceDatePort) {
        return new GetNotificationsService(apimNoticeMessagePort, memberContextPort, referenceDatePort);
    }

    @Bean
    public UpdateNotificationsReadStatusUseCase updateNotificationsReadStatusUseCase(
            ApimNotificationReadStatusPort apimNotificationReadStatusPort,
            MemberContextPort memberContextPort,
            ReferenceDatePort referenceDatePort) {
        return new UpdateNotificationsReadStatusService(
                apimNotificationReadStatusPort, memberContextPort, referenceDatePort);
    }

    @Bean
    public GetContributionSummaryUseCase getContributionSummaryUseCase(
            ApimContributionSummaryPort apimContributionSummaryPort,
            CurrencyDisplayPort currencyDisplayPort,
            ReferenceDatePort referenceDatePort,
            MemberContextPort memberContextPort) {
        return new GetContributionSummaryService(
                apimContributionSummaryPort, currencyDisplayPort, referenceDatePort, memberContextPort);
    }

    @Bean
    public ExportContributionSummaryUseCase exportContributionSummaryUseCase(
            ApimContributionSummaryPort apimContributionSummaryPort,
            CurrencyDisplayPort currencyDisplayPort,
            ReferenceDatePort referenceDatePort,
            MemberContextPort memberContextPort) {
        return new ExportContributionSummaryService(
                apimContributionSummaryPort, currencyDisplayPort, referenceDatePort, memberContextPort);
    }
}
