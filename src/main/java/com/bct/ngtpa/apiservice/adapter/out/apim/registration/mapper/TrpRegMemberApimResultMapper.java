package com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.TrpRegMemberApimEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpRegMemberApimDataItem;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.RegistrationCompletionResult;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.http.HttpStatus;

public class TrpRegMemberApimResultMapper {
    public RegistrationCompletionResult toResult(TrpRegMemberApimEnvelope envelope) {
        if (envelope == null)
            throw invalidEnvelope();
        if (envelope.errMessage() != null && !envelope.errMessage().isBlank()) {
            return RegistrationCompletionResult.rejected(envelope.errMessage().trim());
        }
        List<TrpRegMemberApimDataItem> data = envelope.data();
        if (data == null || data.isEmpty() || !Boolean.TRUE.equals(data.get(0).success())) {
            throw invalidEnvelope();
        }
        TrpRegMemberApimDataItem item = data.get(0);
        return RegistrationCompletionResult.registered(item.createDate(), item.createTime());
    }

    private ApimException invalidEnvelope() {
        return new ApimException(HttpStatus.BAD_GATEWAY, ErrorCodes.APIM_RESPONSE_INVALID,
                "TRPRegMember response is invalid");
    }
}
