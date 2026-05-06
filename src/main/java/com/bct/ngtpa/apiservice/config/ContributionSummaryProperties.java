package com.bct.ngtpa.apiservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "contribution-summary")
@Getter
@Setter
public class ContributionSummaryProperties {

    private final Label totalLabel = new Label();
    private final Headers headers = new Headers();

    @Getter
    @Setter
    public static class Label {
        private String en = "Total";
        private String zh = "";
    }

    @Getter
    @Setter
    public static class Headers {
        private String dealingDate = "Dealing date處理日期";
        private String contributionPeriod = "Contribution Periods供款期";
        private String totalContribution = "Total Contributions供款總額";
    }
}