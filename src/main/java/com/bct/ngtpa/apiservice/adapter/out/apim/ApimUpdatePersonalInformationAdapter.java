package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimCredentialResolutionContext;
import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateMemberInfoApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateMemberInfoApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
import com.bct.ngtpa.apiservice.application.dto.UpdateMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationError;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import com.bct.ngtpa.apiservice.application.port.out.ApimUpdatePersonalInformationPort;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.logging.LogExecution;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ApimUpdatePersonalInformationAdapter implements ApimUpdatePersonalInformationPort {
    private static final String API_NAME = "/ws/NGTPA/v1/TRPUpdMemberInfo";
    private final ApimExchangeExecutor executor;

    @Override
    @LogExecution(value = "apim.updatePersonalInformation", logArgs = true)
    public Mono<List<UpdatePersonalInformationResult>> updateMemberInfo(UpdateMemberInfoCommand command) {
        var request = new ApimExchangeRequest<>(API_NAME, toApimRequest(command),
                UpdateMemberInfoApimRequest.class, UpdateMemberInfoApimDataItem.class,
                ApimDatabaseScope.ACCOUNT_ENV,
                new ApimCredentialResolutionContext(command.userRole(), command.accountEnv()),
                ApimRetryEligibility.IDEMPOTENT_MUTATION);
        return executor.postEnvelope(request).map(response -> toResults(response, command));
    }

    private UpdateMemberInfoApimRequest toApimRequest(UpdateMemberInfoCommand command) {
        return UpdateMemberInfoApimRequest.builder().policyNo(command.policyNo()).certNo(command.certNo())
                .accountEnv(command.accountEnv()).userId(command.userId())
                .userRole(command.userRole() == null ? null : command.userRole().name())
                .applyToAllAccounts(command.applyToAllAccounts()).updateFields(command.updateFields()).build();
    }

    private List<UpdatePersonalInformationResult> toResults(ApimResponseEnvelope<UpdateMemberInfoApimDataItem> response,
            UpdateMemberInfoCommand command) {
        var dataItems = ApimResponseValidator.requireSuccessData(response);
        if (dataItems == null || dataItems.stream().noneMatch(item -> item != null)) {
            throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID,
                    "APIM personal information update response data is missing.");
        }
        var results = dataItems.stream().filter(item -> item != null).map(item -> toResult(item, command)).toList();
        if (results.stream().noneMatch(UpdatePersonalInformationResult::selected)) {
            throw new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID,
                    "APIM personal information update response does not contain the selected account.");
        }
        return results;
    }

    private UpdatePersonalInformationResult toResult(UpdateMemberInfoApimDataItem item, UpdateMemberInfoCommand command) {
        return new UpdatePersonalInformationResult(isSelected(item, command), item.isSuccess(), item.getPolicyNo(),
                item.getCertNo(), item.getEnv(), item.getRefNo(), item.getSubmitDate(), item.getSubmitTime(), toErrors(item));
    }
    private boolean isSelected(UpdateMemberInfoApimDataItem item, UpdateMemberInfoCommand command) {
        return same(item.getPolicyNo(), command.policyNo()) && same(item.getCertNo(), command.certNo())
                && same(item.getEnv(), command.accountEnv());
    }
    private boolean same(String first, String second) { return trim(first).equals(trim(second)); }
    private String trim(String value) { return value == null ? "" : value.trim(); }
    private List<UpdatePersonalInformationError> toErrors(UpdateMemberInfoApimDataItem item) {
        if (item == null || item.getErrors() == null || item.getErrors().isEmpty()) return List.of();
        return item.getErrors().stream().map(error -> UpdatePersonalInformationError.fromPipeSeparatedFields(
                error.getType(), error.getFields(), error.getCode())).toList();
    }
}
