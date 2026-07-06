package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchUnregisteredMemberApimDataItem;
import java.util.List;

public record ApimRegistrationLookupEnvelope(
        String errMessage,
        List<TrpSearchUnregisteredMemberApimDataItem> data) {
    public ApimRegistrationLookupEnvelope {
        errMessage = errMessage == null ? "" : errMessage.trim();
        data = data == null ? List.of() : List.copyOf(data);
    }

    public static ApimRegistrationLookupEnvelope success(List<TrpSearchUnregisteredMemberApimDataItem> data) {
        return new ApimRegistrationLookupEnvelope("", data);
    }

    @Override
    public String toString() {
        return "ApimRegistrationLookupEnvelope[errMessage=" + errMessage + ", dataCount=" + data.size() + "]";
    }
}
