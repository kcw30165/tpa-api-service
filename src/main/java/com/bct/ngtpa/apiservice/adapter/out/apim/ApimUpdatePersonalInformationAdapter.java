package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateMemberInfoApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateMemberInfoApimRequest;
import com.bct.ngtpa.apiservice.application.dto.UpdateMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationAccountResult;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationError;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import com.bct.ngtpa.apiservice.application.port.out.ApimUpdatePersonalInformationPort;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.logging.LogExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.util.List;
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
                                        .map(response -> toResult(response, command))
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
                                                        .map(response -> toResult(response, command));
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
                                .applyToAllAccounts(command.applyToAllAccounts())
                                .updateFields(command.updateFields())
                                .build();
        }

        private UpdatePersonalInformationResult toResult(
                        ApimResponseEnvelope<UpdateMemberInfoApimDataItem> response,
                        UpdateMemberInfoCommand command) {
                var dataItems = ApimResponseValidator.requireValidData(response);

                if (CollectionUtils.isEmpty(dataItems) || dataItems.getFirst() == null) {
                        throw new ApimException(
                                        HttpStatus.BAD_GATEWAY,
                                        ErrorCodes.APIM_RESPONSE_INVALID,
                                        "APIM personal information update response data is missing.");
                }

                var selectedItem = selectedDataItem(dataItems, command);
                var accountResults = dataItems.stream()
                                .filter(item -> item != null)
                                .map(item -> toAccountResult(item, isSelected(item, command)))
                                .toList();

                return new UpdatePersonalInformationResult(
                                selectedItem.isSuccess(),
                                selectedItem.getRefNo(),
                                selectedItem.getSubmitDate(),
                                selectedItem.getSubmitTime(),
                                toErrors(selectedItem),
                                accountResults);
        }

        private UpdateMemberInfoApimDataItem selectedDataItem(
                        List<UpdateMemberInfoApimDataItem> dataItems,
                        UpdateMemberInfoCommand command) {
                if (dataItems.size() == 1) {
                        return dataItems.getFirst();
                }
                return dataItems.stream()
                                .filter(item -> item != null && isSelected(item, command))
                                .findFirst()
                                .orElseThrow(() -> new ApimException(
                                                HttpStatus.BAD_GATEWAY,
                                                ErrorCodes.APIM_RESPONSE_INVALID,
                                                "APIM personal information update response does not contain the selected account."));
        }

        private boolean isSelected(UpdateMemberInfoApimDataItem item, UpdateMemberInfoCommand command) {
                return same(item.getPolicyNo(), command.policyNo())
                                && same(item.getCertNo(), command.certNo())
                                && same(item.getEnv(), command.accountEnv());
        }

        private boolean same(String first, String second) {
                return trim(first).equals(trim(second));
        }

        private String trim(String value) {
                return value == null ? "" : value.trim();
        }

        private UpdatePersonalInformationAccountResult toAccountResult(
                        UpdateMemberInfoApimDataItem item,
                        boolean selected) {
                return new UpdatePersonalInformationAccountResult(
                                item.isSuccess(),
                                selected,
                                item.getPolicyNo(),
                                item.getCertNo(),
                                item.getEnv(),
                                item.getRefNo(),
                                item.getSubmitDate(),
                                item.getSubmitTime(),
                                toErrors(item));
        }

        private List<UpdatePersonalInformationError> toErrors(UpdateMemberInfoApimDataItem item) {
                if (item == null || item.getErrors() == null || item.getErrors().isEmpty()) {
                        return List.of();
                }
                return item.getErrors().stream()
                                .map(error -> UpdatePersonalInformationError.fromPipeSeparatedFields(
                                                error.getType(),
                                                error.getFields(),
                                                error.getCode()))
                                .toList();
        }
}
