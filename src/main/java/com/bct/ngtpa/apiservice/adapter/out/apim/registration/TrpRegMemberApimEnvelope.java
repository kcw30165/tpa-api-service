package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpRegMemberApimDataItem;
import java.util.List;

public record TrpRegMemberApimEnvelope(String errMessage, List<TrpRegMemberApimDataItem> data) {
    public TrpRegMemberApimEnvelope {
        data = data == null ? List.of() : List.copyOf(data);
    }
}
