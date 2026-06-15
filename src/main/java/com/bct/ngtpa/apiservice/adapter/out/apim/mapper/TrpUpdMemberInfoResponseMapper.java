package com.bct.ngtpa.apiservice.adapter.out.apim.mapper;

import com.bct.ngtpa.apiservice.adapter.out.apim.response.TrpUpdMemberInfoData;
import com.bct.ngtpa.apiservice.adapter.out.apim.response.TrpUpdMemberInfoResponse;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationError;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TrpUpdMemberInfoResponseMapper {

    public List<UpdatePersonalInformationResult> toResults(TrpUpdMemberInfoResponse response) {
        if (response == null || response.response() == null
                || response.response().data() == null || response.response().data().isEmpty()) {
            return List.of();
        }
        return response.response().data().stream()
                .filter(data -> data != null)
                .map(data -> toResult(data, false, null, null, null))
                .toList();
    }

    public List<UpdatePersonalInformationResult> toResults(
            TrpUpdMemberInfoResponse response,
            String selectedPolicyNo,
            String selectedCertNo,
            String selectedEnv) {
        if (response == null || response.response() == null
                || response.response().data() == null || response.response().data().isEmpty()) {
            return List.of();
        }
        return response.response().data().stream()
                .filter(data -> data != null)
                .map(data -> toResult(
                        data,
                        isSelected(data, selectedPolicyNo, selectedCertNo, selectedEnv),
                        selectedPolicyNo,
                        selectedCertNo,
                        selectedEnv))
                .toList();
    }

    /**
     * Compatibility adapter for older unit tests that exercise this legacy mapper directly.
     * Production update flow maps APIM response.data[] in ApimUpdatePersonalInformationAdapter.
     */
    public UpdatePersonalInformationResult toResult(TrpUpdMemberInfoResponse response) {
        return toResults(response).stream()
                .findFirst()
                .orElseGet(() -> new UpdatePersonalInformationResult(
                        false,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of()));
    }

    private UpdatePersonalInformationResult toResult(
            TrpUpdMemberInfoData data,
            boolean selected,
            String selectedPolicyNo,
            String selectedCertNo,
            String selectedEnv) {
        return new UpdatePersonalInformationResult(
                selected,
                Boolean.TRUE.equals(data.success()),
                selectedPolicyNo,
                selectedCertNo,
                selectedEnv,
                data.refNo(),
                data.submitDate(),
                data.submitTime(),
                toErrors(data));
    }

    private boolean isSelected(
            TrpUpdMemberInfoData data,
            String selectedPolicyNo,
            String selectedCertNo,
            String selectedEnv) {
        // TrpUpdMemberInfoData legacy DTO does not expose policy/cert/env fields.
        // The overload exists for forward compatibility; production selection is handled in ApimUpdatePersonalInformationAdapter.
        return false;
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
