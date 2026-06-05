package com.bct.ngtpa.apiservice.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.values;

import com.bct.ngtpa.apiservice.application.dto.GetReferenceDataCountriesCommand;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDataCountriesResult;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDataCountryItem;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDataOption;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDataCountriesPort;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

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

        @Test
        void nullCommandDefaultsToEnglishAndSkipsNullOrBlankItems() {
                ApimReferenceDataCountriesPort port = () -> Mono.just(Arrays.asList(
                                null,
                                country("HKG", "Hong Kong", "香港", "852"),
                                country(" ", "Blank Code", "空白", "000"),
                                country("MAC", " ", "澳門", "853"),
                                country("CHN", "China", "中國", " ")));

                StepVerifier.create(new GetReferenceDataCountriesService(port).execute(null))
                                .assertNext(result -> {
                                        assertThat(values(result.countries())).containsExactly("HKG");
                                        assertThat(texts(result.countries())).containsExactly("Hong Kong");
                                        assertThat(values(result.callingCodes())).containsExactly("852");
                                })
                                .verifyComplete();
        }

        @Test
        void zhHkUsesChineseNameButFallsBackToEnglishWhenChineseNameIsBlank() {
                ApimReferenceDataCountriesPort port = () -> Mono.just(List.of(
                                country("HKG", "Hong Kong", "香港", "852"),
                                country("USA", "United States", " ", "1")));

                StepVerifier.create(new GetReferenceDataCountriesService(port)
                                .execute(new GetReferenceDataCountriesCommand("ACC-123", " zh_HK ")))
                                .assertNext(result -> assertThat(texts(result.countries()))
                                                .containsExactly("香港", "United States"))
                                .verifyComplete();
        }

        @Test
        void emptyMonoAndEmptyListReturnEmptyResult() {
                StepVerifier.create(
                                new GetReferenceDataCountriesService(() -> Mono.<List<ReferenceDataCountryItem>>empty())
                                                .execute(new GetReferenceDataCountriesCommand("ACC-123", "en")))
                                .assertNext(result -> {
                                        assertThat(result.countries()).isEmpty();
                                        assertThat(result.callingCodes()).isEmpty();
                                })
                                .verifyComplete();

                StepVerifier.create(new GetReferenceDataCountriesService(() -> Mono.just(List.of()))
                                .execute(new GetReferenceDataCountriesCommand("ACC-123", "en")))
                                .assertNext(result -> {
                                        assertThat(result.countries()).isEmpty();
                                        assertThat(result.callingCodes()).isEmpty();
                                })
                                .verifyComplete();
        }

        // private static ReferenceDataCountryItem country(String code, String en,
        // String zh, String callingCode) {
        // return new ReferenceDataCountryItem(code, en, zh, callingCode);
        // }

        private static List<String> values(List<ReferenceDataOption> options) {
                return options.stream().map(ReferenceDataOption::value).toList();
        }

        private static List<String> texts(List<ReferenceDataOption> options) {
                return options.stream().map(ReferenceDataOption::text).toList();
        }

        private static ReferenceDataCountryItem country(
                        String countryCode,
                        String countryNameEng,
                        String countryNameChi,
                        String callingCode) {
                return new ReferenceDataCountryItem(countryCode, countryNameEng, countryNameChi, callingCode);
        }
}
