package com.bct.ngtpa.apiservice.adapter.out.apim.mapper;

import com.bct.ngtpa.apiservice.adapter.out.apim.response.TrpUpdMemberInfoData;
import com.bct.ngtpa.apiservice.adapter.out.apim.response.TrpUpdMemberInfoResponse;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationError;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TrpUpdMemberInfoResponseMapper {

    public UpdatePersonalInformationResult toResult(TrpUpdMemberInfoResponse response) {
        TrpUpdMemberInfoData data = firstData(response);
        if (data == null) {
            return new UpdatePersonalInformationResult(false, null, null, null, List.of());
        }
        return new UpdatePersonalInformationResult(
                Boolean.TRUE.equals(data.success()),
                data.refNo(),
                data.submitDate(),
                data.submitTime(),
                toErrors(data));
    }

    private TrpUpdMemberInfoData firstData(TrpUpdMemberInfoResponse response) {
        if (response == null || response.response() == null
                || response.response().data() == null || response.response().data().isEmpty()) {
            return null;
        }
        return response.response().data().get(0);
    }

    private List<UpdatePersonalInformationError> toErrors(TrpUpdMemberInfoData data) {
        if (data.errors() == null || data.errors().isEmpty()) {
            return List.of();
        }
        return data.errors().stream()
                .map(error -> UpdatePersonalInformationError.fromPipeSeparatedFields(
                        error.type(),
                        error.fields(),
                        error.code()))
                .toList();
    }
}
