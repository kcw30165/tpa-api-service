package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateMemberInfoApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateMemberInfoApimError;
import com.bct.ngtpa.apiservice.application.dto.UpdateMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import com.bct.ngtpa.apiservice.exception.ApimException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ApimUpdatePersonalInformationAdapterTest {

    @Test
    void mapsEveryApimDataItemToFlatUpdateResultAndMarksSelectedAccount() {
        ApimUpdatePersonalInformationAdapter adapter = new ApimUpdatePersonalInformationAdapter(null, null, null, null);

        List<UpdatePersonalInformationResult> results = invokeToResults(adapter, envelope(List.of(
                item(true, "00000000118", "2", "DB", "260001373", List.of()),
                item(false, "00000000118", "3", "DB", "260001374", List.of(
                        new UpdateMemberInfoApimError("FIELD", "email", "REQUIRED"),
                        new UpdateMemberInfoApimError("CROSS_FIELD", "addr1|addr2", "AT_LEAST_ONE_REQUIRED"))))),
                command("00000000118", "3", "DB"));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).selected()).isFalse();
        assertThat(results.get(0).success()).isTrue();
        assertThat(results.get(0).policyNo()).isEqualTo("00000000118");
        assertThat(results.get(0).certNo()).isEqualTo("2");
        assertThat(results.get(0).env()).isEqualTo("DB");
        assertThat(results.get(0).refNo()).isEqualTo("260001373");

        assertThat(results.get(1).selected()).isTrue();
        assertThat(results.get(1).success()).isFalse();
        assertThat(results.get(1).refNo()).isEqualTo("260001374");
        assertThat(results.get(1).errors()).hasSize(2);
        assertThat(results.get(1).errors().get(0).fields()).containsExactly("email");
        assertThat(results.get(1).errors().get(1).fields()).containsExactly("addr1", "addr2");
    }

    @Test
    void rejectsApimResponseWhenSelectedAccountIsMissing() {
        ApimUpdatePersonalInformationAdapter adapter = new ApimUpdatePersonalInformationAdapter(null, null, null, null);

        assertThatThrownBy(() -> invokeToResults(adapter, envelope(List.of(
                item(true, "00000000118", "2", "DB", "260001373", List.of()))),
                command("00000000118", "3", "DB")))
                .isInstanceOf(ApimException.class)
                .hasMessageContaining("selected account");
    }

    @SuppressWarnings("unchecked")
    private List<UpdatePersonalInformationResult> invokeToResults(
            ApimUpdatePersonalInformationAdapter adapter,
            ApimResponseEnvelope<UpdateMemberInfoApimDataItem> envelope,
            UpdateMemberInfoCommand command) {
        return (List<UpdatePersonalInformationResult>) ReflectionTestUtils.invokeMethod(
                adapter,
                "toResults",
                envelope,
                command);
    }

    private ApimResponseEnvelope<UpdateMemberInfoApimDataItem> envelope(List<UpdateMemberInfoApimDataItem> data) {
        return ApimResponseEnvelope.<UpdateMemberInfoApimDataItem>builder()
                .response(ApimResponseBody.<UpdateMemberInfoApimDataItem>builder()
                        .errMessage("")
                        .data(data)
                        .build())
                .build();
    }

    private UpdateMemberInfoApimDataItem item(
            boolean success,
            String policyNo,
            String certNo,
            String env,
            String refNo,
            List<UpdateMemberInfoApimError> errors) {
        return UpdateMemberInfoApimDataItem.builder()
                .success(success)
                .policyNo(policyNo)
                .certNo(certNo)
                .env(env)
                .refNo(refNo)
                .submitDate("2025-12-31")
                .submitTime("15:42:52")
                .errors(errors)
                .build();
    }

    private UpdateMemberInfoCommand command(String policyNo, String certNo, String env) {
        return new UpdateMemberInfoCommand(
                env,
                policyNo,
                certNo,
                "actor-user",
                "MEMBER",
                true,
                Map.of("email", "member@example.com"));
    }
}
