package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetCountryListApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetCountryListDataItem;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDataCountryItem;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDataCountriesPort;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.logging.LogExecution;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ApimReferenceDataCountriesAdapter implements ApimReferenceDataCountriesPort {

    private static final String API_NAME = "/ws/NGTPA/v1/TRPGetCountryList";

    private final ApimWebClientFacade apimWebClientFacade;
    private final ApimPayloadCryptoService apimPayloadCryptoService;
    private final com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties apimProperties;

    @Override
    @LogExecution(value = "apim.fetchCountryList", logArgs = true)
    public Mono<List<ReferenceDataCountryItem>> fetchCountryList() {
        var request = apimPayloadCryptoService.encryptRequest(
                API_NAME,
                newRequest(),
                GetCountryListApimRequest.class,
                null);

        return apimWebClientFacade.post(API_NAME, request)
                .map(body -> apimPayloadCryptoService.decryptResponseEnvelope(
                        API_NAME,
                        body,
                        GetCountryListDataItem.class,
                        null))
                .map(this::toCountryItems);
    }

    private GetCountryListApimRequest newRequest() {
        return new GetCountryListApimRequest();
    }

    private List<ReferenceDataCountryItem> toCountryItems(ApimResponseEnvelope<GetCountryListDataItem> response) {
        var payload = response != null ? response.getResponse() : null;
        if (payload == null) {
            throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID,
                    "APIM response payload is missing.");
        }
        if (StringUtils.hasText(payload.getErrMessage())) {
            throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID,
                    payload.getErrMessage());
        }
        if (CollectionUtils.isEmpty(payload.getData())) {
            return List.of();
        }

        return payload.getData().stream()
                .filter(Objects::nonNull)
                .map(item -> new ReferenceDataCountryItem(
                        item.getCountryCode(),
                        item.getCountryNameEng(),
                        item.getCountryNameChi(),
                        item.getCallingCode()))
                .toList();
    }
}
