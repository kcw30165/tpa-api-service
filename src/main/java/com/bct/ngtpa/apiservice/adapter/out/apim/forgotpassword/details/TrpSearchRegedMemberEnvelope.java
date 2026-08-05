package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.dto.TrpSearchRegedMemberDataItem;
import java.util.List;
public record TrpSearchRegedMemberEnvelope(String errMessage, List<TrpSearchRegedMemberDataItem> data) {
    public TrpSearchRegedMemberEnvelope { data = data == null ? List.of() : List.copyOf(data); }
}
