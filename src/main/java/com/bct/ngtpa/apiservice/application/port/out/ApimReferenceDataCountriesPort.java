package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.ReferenceDataCountryItem;
import java.util.List;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface ApimReferenceDataCountriesPort {

    Mono<List<ReferenceDataCountryItem>> fetchCountryList();
}
