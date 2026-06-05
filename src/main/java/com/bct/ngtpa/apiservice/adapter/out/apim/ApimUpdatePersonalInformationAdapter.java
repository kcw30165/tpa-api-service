package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateMemberInfoApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateMemberInfoApimRequest;
import com.bct.ngtpa.apiservice.application.dto.UpdateMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import com.bct.ngtpa.apiservice.application.port.out.ApimUpdatePersonalInformationPort;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.logging.LogExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ApimUpdatePersonalInformationAdapter implements ApimUpdatePersonalInformationPort {

        private static final String API_NAME = "/ws/NGTPA/v1/TRPUpdMemberInfo";

        private final ApimWebClientFacade apimWebClientFacade;
        private final ApimCertificateService apimCertificateService;
        private final ApimPayloadCryptoService apimPayloadCryptoService;
        private final ApimProperties apimProperties;

        @Override
        @LogExecution(value = "apim.updatePersonalInformation", logArgs = true)
        public Mono<UpdatePersonalInformationResult> updateMemberInfo(UpdateMemberInfoCommand command) {
                if (!apimProperties.getEncryption().isEnabled()) {
                        var request = apimPayloadCryptoService.encryptRequest(
                                        API_NAME, toApimRequest(command), UpdateMemberInfoApimRequest.class, null);
                        return apimWebClientFacade.post(API_NAME, request)
                                        .map(body -> apimPayloadCryptoService.decryptResponseEnvelope(
                                                        API_NAME, body, UpdateMemberInfoApimDataItem.class, null))
                                        .map(this::toResult)
                                        .onErrorMap(ApimCryptoException.class,
                                                        ex -> new ApimException(HttpStatus.INTERNAL_SERVER_ERROR,
                                                                        ErrorCodes.SYSTEM_UNEXPECTED, ex.getMessage(),
                                                                        ex));
                }

                return apimCertificateService.getBctPublicKey()
                                .flatMap(publicKey -> {
                                        var request = apimPayloadCryptoService.encryptRequest(
                                                        API_NAME, toApimRequest(command),
                                                        UpdateMemberInfoApimRequest.class, publicKey);
                                        return apimWebClientFacade.post(API_NAME, request)
                                                        .map(body -> apimPayloadCryptoService.decryptResponseEnvelope(
                                                                        API_NAME, body,
                                                                        UpdateMemberInfoApimDataItem.class, publicKey))
                                                        .map(this::toResult);
                                })
                                .onErrorMap(ApimCryptoException.class,
                                                ex -> new ApimException(HttpStatus.INTERNAL_SERVER_ERROR,
                                                                ErrorCodes.SYSTEM_UNEXPECTED, ex.getMessage(), ex));
        }

        private UpdateMemberInfoApimRequest toApimRequest(UpdateMemberInfoCommand command) {
                return UpdateMemberInfoApimRequest.builder()
                                .policyNo(command.policyNo())
                                .certNo(command.certNo())
                                .accountEnv(command.accountEnv())
                                .userId(command.userId())
                                .userRole(command.userRole())
                                .updateFields(command.updateFields())
                                .build();
        }

        private UpdatePersonalInformationResult toResult(
                        ApimResponseEnvelope<UpdateMemberInfoApimDataItem> response) {

                var payload = response != null ? response.getResponse() : null;

                if (payload == null) {
                        throw new ApimException(
                                        HttpStatus.BAD_GATEWAY,
                                        ErrorCodes.APIM_RESPONSE_INVALID,
                                        "APIM response payload is missing.");
                }

                if (StringUtils.hasText(payload.getErrMessage())) {
                        throw new ApimException(
                                        HttpStatus.BAD_GATEWAY,
                                        ErrorCodes.APIM_RESPONSE_INVALID,
                                        payload.getErrMessage());
                }

                var dataItems = payload.getData();

                if (CollectionUtils.isEmpty(dataItems) || dataItems.getFirst() == null) {
                        throw new ApimException(
                                        HttpStatus.BAD_GATEWAY,
                                        ErrorCodes.APIM_RESPONSE_INVALID,
                                        "APIM personal information update response data is missing.");
                }

                var firstItem = dataItems.getFirst();

                if (!firstItem.isSuccess()) {
                        throw new ApimException(
                                        HttpStatus.BAD_GATEWAY,
                                        ErrorCodes.APIM_RESPONSE_INVALID,
                                        "APIM personal information update was not successful.");
                }

                return new UpdatePersonalInformationResult(
                                firstItem.isSuccess(),
                                firstItem.getRefNo(),
                                firstItem.getSubmitDate(),
                                firstItem.getSubmitTime());
        }
}
