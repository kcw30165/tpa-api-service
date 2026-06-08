package com.bct.ngtpa.apiservice.adapter.out.apim.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.response.TrpUpdMemberInfoData;
import com.bct.ngtpa.apiservice.adapter.out.apim.response.TrpUpdMemberInfoError;
import com.bct.ngtpa.apiservice.adapter.out.apim.response.TrpUpdMemberInfoResponse;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrpUpdMemberInfoResponseMapperTest {

    private final TrpUpdMemberInfoResponseMapper mapper = new TrpUpdMemberInfoResponseMapper();

    @Test
    void mapsSuccessResponse() {
        TrpUpdMemberInfoResponse response = new TrpUpdMemberInfoResponse(
                new TrpUpdMemberInfoResponse.Response("", List.of(
                        new TrpUpdMemberInfoData(true, "990000001", "2026-06-07", "08:36:51", null))));

        UpdatePersonalInformationResult result = mapper.toResult(response);

        assertThat(result.success()).isTrue();
        assertThat(result.refNo()).isEqualTo("990000001");
        assertThat(result.submitDate()).isEqualTo("2026-06-07");
        assertThat(result.submitTime()).isEqualTo("08:36:51");
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void mapsValidationFailureResponseAndSplitsPipeSeparatedFields() {
        TrpUpdMemberInfoResponse response = new TrpUpdMemberInfoResponse(
                new TrpUpdMemberInfoResponse.Response("", List.of(
                        new TrpUpdMemberInfoData(false, null, null, null, List.of(
                                new TrpUpdMemberInfoError("FIELD", "email", "REQUIRED"),
                                new TrpUpdMemberInfoError("CROSS_FIELD", "addr1|addr2", "AT_LEAST_ONE_REQUIRED"))))));

        UpdatePersonalInformationResult result = mapper.toResult(response);

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors().get(0).fields()).containsExactly("email");
        assertThat(result.errors().get(1).fields()).containsExactly("addr1", "addr2");
    }
}
