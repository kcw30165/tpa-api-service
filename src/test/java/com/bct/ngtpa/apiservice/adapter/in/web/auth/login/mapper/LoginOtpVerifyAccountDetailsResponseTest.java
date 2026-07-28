package com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto.LoginOtpAccountResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class LoginOtpVerifyAccountDetailsResponseTest {
    @Test
    void accountSelectionResponseExposesRequiredValidatedAccountDetails() throws Exception {
        var account = new LoginOtpAccountResponse(
                "acc_abcdefghijklmnopqrstuvwx",
                "00000000012",
                "0001",
                "DB",
                "TR",
                "OE");

        String json = new ObjectMapper().writeValueAsString(account);

        assertThat(json)
                .contains("\"accountRef\":\"acc_abcdefghijklmnopqrstuvwx\"")
                .contains("\"policyNo\":\"00000000012\"")
                .contains("\"certNo\":\"0001\"")
                .contains("\"accountEnv\":\"DB\"")
                .contains("\"trust\":\"TR\"")
                .contains("\"schemeType\":\"OE\"")
                .doesNotContain("rawPolicyNo", "rawCertNo", "trustCode");
    }

    @Test
    void responseRecordHasExactlyTheApprovedAccountFields() {
        assertThat(Arrays.stream(LoginOtpAccountResponse.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName))
                .containsExactly(
                        "accountRef",
                        "policyNo",
                        "certNo",
                        "accountEnv",
                        "trust",
                        "schemeType");
    }
}
