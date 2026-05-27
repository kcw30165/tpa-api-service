package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetWebSysDateApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetWebSysDateApimRequest;
import com.bct.ngtpa.apiservice.application.port.out.ApimReferenceDateRefreshPort;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.logging.LogExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
@RequiredArgsConstructor
public class ApimReferenceDateRefreshAdapter implements ApimReferenceDateRefreshPort {

    private static final String API_NAME = "/ws/NGTPA/v1/TRPGetWebSysDate";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ApimWebClientFacade apimWebClientFacade;
    private final ApimCertificateService apimCertificateService;
    private final ApimPayloadCryptoService apimPayloadCryptoService;
    private final ApimProperties apimProperties;

    @Override
    @LogExecution(value = "apim.fetchReferenceDate", logArgs = true)
    public Mono<LocalDate> fetchReferenceDate(String accountEnv) {
        if (!apimProperties.getEncryption().isEnabled()) {
            var request = apimPayloadCryptoService.encryptRequest(
                    API_NAME, toApimRequest(accountEnv), GetWebSysDateApimRequest.class, null);
            return apimWebClientFacade.post(API_NAME, request)
                    .map(body -> apimPayloadCryptoService.decryptResponseEnvelope(
                            API_NAME, body, GetWebSysDateApimDataItem.class, null))
                    .map(this::toReferenceDate)
                    .onErrorMap(ApimCryptoException.class, ex ->
                            new ApimException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.SYSTEM_UNEXPECTED,
                                    ex.getMessage(), ex));
        }

        return apimCertificateService.getBctPublicKey()
                .flatMap(publicKey -> {
                    var request = apimPayloadCryptoService.encryptRequest(
                            API_NAME, toApimRequest(accountEnv), GetWebSysDateApimRequest.class, publicKey);
                    return apimWebClientFacade.post(API_NAME, request)
                            .map(body -> apimPayloadCryptoService.decryptResponseEnvelope(
                                    API_NAME, body, GetWebSysDateApimDataItem.class, publicKey))
                            .map(this::toReferenceDate);
                })
                .onErrorMap(ApimCryptoException.class, ex ->
                        new ApimException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.SYSTEM_UNEXPECTED,
                                ex.getMessage(), ex));
    }

    private GetWebSysDateApimRequest toApimRequest(String accountEnv) {
        return GetWebSysDateApimRequest.builder()
                .accountEnv(accountEnv)
                .build();
    }

    private LocalDate toReferenceDate(ApimResponseEnvelope<GetWebSysDateApimDataItem> response) {
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
            throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID,
                    "APIM response data is missing.");
        }

        GetWebSysDateApimDataItem firstItem = payload.getData().getFirst();
        String sysDate = firstItem == null ? null : firstItem.getSysDate();
        if (!StringUtils.hasText(sysDate)) {
            throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID,
                    "APIM sys-date is missing.");
        }

        try {
            return LocalDate.parse(sysDate.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID,
                    "APIM sys-date must use dd/MM/yyyy format.", ex);
        }
    }
}