package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.GetReferenceDataCountriesCommand;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDataCountriesResult;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDataCountryItem;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDataOption;
import com.bct.ngtpa.apiservice.application.port.in.GetReferenceDataCountriesUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDataCountriesPort;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class GetReferenceDataCountriesService implements GetReferenceDataCountriesUseCase {

    private static final String ZH_HK = "zh_HK";

    private final ApimReferenceDataCountriesPort apimReferenceDataCountriesPort;

    @Override
    public Mono<ReferenceDataCountriesResult> execute(GetReferenceDataCountriesCommand command) {
        return apimReferenceDataCountriesPort.fetchCountryList()
                .defaultIfEmpty(List.of())
                .map(items -> mapResult(items, command != null ? command.language() : null));
    }

    private ReferenceDataCountriesResult mapResult(List<ReferenceDataCountryItem> items, String language) {
        if (items == null || items.isEmpty()) {
            return new ReferenceDataCountriesResult(List.of(), List.of());
        }

        boolean zhHk = isZhHk(language);
        List<ReferenceDataOption> countries = new ArrayList<>();
        List<ReferenceDataOption> callingCodes = new ArrayList<>();

        for (ReferenceDataCountryItem item : items) {
            if (item == null) {
                continue;
            }

            String text = resolveText(item, zhHk);
            if (!hasText(item.countryCode()) || !hasText(item.callingCode()) || !hasText(text)) {
                continue;
            }

            countries.add(new ReferenceDataOption(item.countryCode(), text));
            callingCodes.add(new ReferenceDataOption(item.callingCode(), text));
        }

        return new ReferenceDataCountriesResult(List.copyOf(countries), List.copyOf(callingCodes));
    }

    private String resolveText(ReferenceDataCountryItem item, boolean zhHk) {
        if (zhHk) {
            return hasText(item.countryNameChi()) ? item.countryNameChi() : item.countryNameEng();
        }
        return item.countryNameEng();
    }

    private boolean isZhHk(String language) {
        return ZH_HK.equalsIgnoreCase(trimToNull(language));
    }

    private boolean hasText(String value) {
        return trimToNull(value) != null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
