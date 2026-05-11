package com.bct.ngtpa.apiservice.adapter.in.web.config;

import com.bct.ngtpa.apiservice.adapter.in.web.config.ContributionSummaryProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContributionWebDisplayConfigProvider {

    private final ContributionSummaryProperties properties;

    public ContributionWebDisplayConfig get() {
        return new ContributionWebDisplayConfig(
            properties.getTotalLabel().getEn(),
            properties.getTotalLabel().getZh(),
            properties.getHeaders().getDealingDate(),
            properties.getHeaders().getContributionPeriod(),
            properties.getHeaders().getTotalContribution()
        );
    }
}
