package com.bct.ngtpa.apiservice.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bct.ngtpa.apiservice.application.dto.GetReferenceDataCountriesCommand;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDataCountriesResult;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDataCountryItem;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDataCountriesPort;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Mono;
import org.junit.jupiter.api.Test;

class GetReferenceDataCountriesServiceTest {

    @Test
    void callsOutboundPortOnce() {
        AtomicInteger calls = new AtomicInteger();
        ApimReferenceDataCountriesPort port = () -> {
            calls.incrementAndGet();
            return Mono.just(List.of(country("HKG", "Hong Kong", "香港", "852")));
        };

        GetReferenceDataCountriesService service = new GetReferenceDataCountriesService(port);

        service.execute(new GetReferenceDataCountriesCommand("ACC-123", "en")).block();

        assertEquals(1, calls.get());
    }

    @Test
    void mapsEnglishWhenLanguageIsEnglish() {
        ApimReferenceDataCountriesPort port = () -> Mono.just(List.of(
                country("HKG", "Hong Kong", "香港", "852"),
                country("CHN", "China", "中國", "86")));

        GetReferenceDataCountriesService service = new GetReferenceDataCountriesService(port);

        ReferenceDataCountriesResult result = service
                .execute(new GetReferenceDataCountriesCommand("ACC-123", "en"))
                .block();

        assertEquals("HKG", result.countries().get(0).value());
        assertEquals("Hong Kong", result.countries().get(0).text());
        assertEquals("CHN", result.countries().get(1).value());
        assertEquals("China", result.countries().get(1).text());

        assertEquals("852", result.callingCodes().get(0).value());
        assertEquals("Hong Kong", result.callingCodes().get(0).text());
        assertEquals("86", result.callingCodes().get(1).value());
        assertEquals("China", result.callingCodes().get(1).text());
    }

    @Test
    void mapsChineseWhenLanguageIsZhHk() {
        ApimReferenceDataCountriesPort port = () -> Mono.just(List.of(
                country("HKG", "Hong Kong", "香港", "852"),
                country("CHN", "China", "中國", "86")));

        GetReferenceDataCountriesService service = new GetReferenceDataCountriesService(port);

        ReferenceDataCountriesResult result = service
                .execute(new GetReferenceDataCountriesCommand("ACC-123", "zh_HK"))
                .block();

        assertEquals("香港", result.countries().get(0).text());
        assertEquals("中國", result.countries().get(1).text());
        assertEquals("香港", result.callingCodes().get(0).text());
        assertEquals("中國", result.callingCodes().get(1).text());
    }

    @Test
    void preservesApimOrderForCountriesAndCallingCodes() {
        ApimReferenceDataCountriesPort port = () -> Mono.just(List.of(
                country("ZZZ", "Zulu", "祖魯", "999"),
                country("AAA", "Alpha", "阿爾法", "111")));

        GetReferenceDataCountriesService service = new GetReferenceDataCountriesService(port);

        ReferenceDataCountriesResult result = service
                .execute(new GetReferenceDataCountriesCommand("ACC-123", "en"))
                .block();

        assertEquals(List.of("ZZZ", "AAA"), result.countries().stream().map(v -> v.value()).toList());
        assertEquals(List.of("999", "111"), result.callingCodes().stream().map(v -> v.value()).toList());
    }

    @Test
    void handlesNullOrBlankApimDataAsEmptyLists() {
                ApimReferenceDataCountriesPort nullPort = Mono::empty;
        GetReferenceDataCountriesService nullService = new GetReferenceDataCountriesService(nullPort);

        ReferenceDataCountriesResult nullResult = nullService
                .execute(new GetReferenceDataCountriesCommand("ACC-123", "en"))
                .block();

        assertTrue(nullResult.countries().isEmpty());
        assertTrue(nullResult.callingCodes().isEmpty());

        ApimReferenceDataCountriesPort blankPort = () -> Mono.just(List.of(
                country("", "", "", ""),
                country(null, null, null, null)));
        GetReferenceDataCountriesService blankService = new GetReferenceDataCountriesService(blankPort);

        ReferenceDataCountriesResult blankResult = blankService
                .execute(new GetReferenceDataCountriesCommand("ACC-123", "en"))
                .block();

        assertTrue(blankResult.countries().isEmpty());
        assertTrue(blankResult.callingCodes().isEmpty());
    }

    private static ReferenceDataCountryItem country(
            String countryCode,
            String countryNameEng,
            String countryNameChi,
            String callingCode) {
        return new ReferenceDataCountryItem(countryCode, countryNameEng, countryNameChi, callingCode);
    }
}
