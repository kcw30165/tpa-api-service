package com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchUnregMemberApimDataItem;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupAccount;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupResult;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupStatus;
import java.util.List;

public class TrpSearchUnregMemberApimResultMapper {
    public RegistrationLookupResult toResult(String errMessage, List<TrpSearchUnregMemberApimDataItem> data) {
        String safeError = errMessage == null || errMessage.isBlank() ? null : errMessage.trim();
        List<TrpSearchUnregMemberApimDataItem> items = data == null ? List.of() : data;
        if (safeError == null && !items.isEmpty()) {
            return new RegistrationLookupResult(
                    RegistrationLookupStatus.ELIGIBLE,
                    items.stream().map(this::toAccount).toList(),
                    null);
        }
        if (safeError == null) {
            return new RegistrationLookupResult(RegistrationLookupStatus.NOT_FOUND_OR_NOT_ELIGIBLE, List.of(), null);
        }
        if ("err.tpa.member.not.found".equals(safeError)) {
            return new RegistrationLookupResult(RegistrationLookupStatus.NOT_FOUND_OR_NOT_ELIGIBLE, List.of(), safeError);
        }
        return new RegistrationLookupResult(RegistrationLookupStatus.UPSTREAM_BUSINESS_REJECTED, List.of(), safeError);
    }

    private RegistrationLookupAccount toAccount(TrpSearchUnregMemberApimDataItem item) {
        return new RegistrationLookupAccount(
                item.email(),
                item.mobileNumber(),
                item.env(),
                item.trustCode());
    }
}
