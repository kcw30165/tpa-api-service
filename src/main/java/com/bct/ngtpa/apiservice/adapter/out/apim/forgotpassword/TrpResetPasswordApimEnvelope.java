package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword;

import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.dto.TrpResetPasswordApimDataItem;
import java.util.List;

public record TrpResetPasswordApimEnvelope(String errMessage, List<TrpResetPasswordApimDataItem> data) {
    public TrpResetPasswordApimEnvelope {
        data = data == null ? List.of() : List.copyOf(data);
    }
}