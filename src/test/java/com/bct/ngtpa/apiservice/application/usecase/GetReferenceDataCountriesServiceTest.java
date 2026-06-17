package com.bct.ngtpa.apiservice.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bct.ngtpa.apiservice.application.dto.GetReferenceDataCountriesCommand;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDataCountryItem;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDataOption;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDataCountriesPort;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class GetReferenceDataCountriesServiceTest {

    @Test
    void mapsEnglishCountriesAndCallingCodesWithoutPortalContext() {
        ApimReferenceDataCountriesPort apimPort = () -> Mono.just(List.of(
                new ReferenceDataCountryItem("HK", "Hong Kong", "香港", "+852"),
                new ReferenceDataCountryItem("JP", "Japan", "日本", "+81"),
                new ReferenceDataCountryItem("", "Missing", "缺失", "+00"),
                new ReferenceDataCountryItem("MO", "Macau", "澳門", " ")));

        var result = new GetReferenceDataCountriesService(apimPort)
                .execute(new GetReferenceDataCountriesCommand("en"))
                .block();

        assertEquals(List.of("HK", "JP"), result.countries().stream().map(ReferenceDataOption::value).toList());
        assertEquals(List.of("Hong Kong", "Japan"), result.countries().stream().map(ReferenceDataOption::text).toList());
        assertEquals(List.of("+852", "+81"), result.callingCodes().stream().map(ReferenceDataOption::value).toList());
    }

    @Test
    void mapsTraditionalChineseAndFallsBackToEnglishWhenChineseNameBlank() {
        ApimReferenceDataCountriesPort apimPort = () -> Mono.just(List.of(
                new ReferenceDataCountryItem("HK", "Hong Kong", "香港", "+852"),
                new ReferenceDataCountryItem("US", "United States", "", "+1")));

        var result = new GetReferenceDataCountriesService(apimPort)
                .execute(new GetReferenceDataCountriesCommand("zh_HK"))
                .block();

        assertEquals("香港", result.countries().get(0).text());
        assertEquals("United States", result.countries().get(1).text());
    }

    @Test
    void emptyCountryListProducesEmptyResult() {
        ApimReferenceDataCountriesPort apimPort = () -> Mono.empty();

        var result = new GetReferenceDataCountriesService(apimPort)
                .execute(new GetReferenceDataCountriesCommand("en"))
                .block();

        assertTrue(result.countries().isEmpty());
        assertTrue(result.callingCodes().isEmpty());
    }
}
