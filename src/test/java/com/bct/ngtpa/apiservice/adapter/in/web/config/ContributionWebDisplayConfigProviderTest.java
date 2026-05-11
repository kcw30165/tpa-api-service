package com.bct.ngtpa.apiservice.adapter.in.web.config;

import com.bct.ngtpa.apiservice.adapter.in.web.config.ContributionSummaryProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContributionWebDisplayConfigProviderTest {

    @Test
    void adaptsDefaultPropertiesIntoDisplayConfig() {
        var provider = new ContributionWebDisplayConfigProvider(new ContributionSummaryProperties());

        var config = provider.get();

        assertEquals("Total Contributions", config.totalLabelEn());
        assertEquals("供款總額", config.totalLabelZh());
        assertEquals("Dealing date處理日期", config.dealingDateHeader());
        assertEquals("Contribution Periods供款期", config.contributionPeriodHeader());
        assertEquals("Total Contributions供款總額", config.totalContributionHeader());
    }

    @Test
    void adaptsCustomPropertiesIntoDisplayConfig() {
        var properties = new ContributionSummaryProperties();
        properties.getTotalLabel().setEn("Custom En");
        properties.getTotalLabel().setZh("自訂中文");
        properties.getHeaders().setDealingDate("Date Header");
        properties.getHeaders().setContributionPeriod("Period Header");
        properties.getHeaders().setTotalContribution("Total Header");

        var provider = new ContributionWebDisplayConfigProvider(properties);
        var config = provider.get();

        assertEquals("Custom En", config.totalLabelEn());
        assertEquals("自訂中文", config.totalLabelZh());
        assertEquals("Date Header", config.dealingDateHeader());
        assertEquals("Period Header", config.contributionPeriodHeader());
        assertEquals("Total Header", config.totalContributionHeader());
    }
}
