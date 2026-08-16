package com.bct.ngtpa.apiservice.adapter.out.apim.login.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.login.account.dto.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.account.LoginAccountAuthorizationCommand;
import org.junit.jupiter.api.Test;

class TrpGetAccountListContractMapperTest {
    private final TrpGetAccountListContractMapper mapper = new TrpGetAccountListContractMapper();

    @Test
    void mapsExactRequestContractWithoutChangingProtectedValues() {
        var request = mapper.toRequest(new LoginAccountAuthorizationCommand(
                "member01", "MBR", "cust-id", "member01", "req-1", com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR));
        assertThat(request.custId()).isEqualTo("member01");
        assertThat(request.custType()).isEqualTo("MBR");
        assertThat(request.searchKey()).isEqualTo("cust-id");
        assertThat(request.searchValue()).isEqualTo("member01");
        assertThat(request.toString()).doesNotContain("member01");
    }

    @Test
    void mapsExactRawResponseFieldsIncludingIntegerCertificateNumber() {
        var mapped = mapper.toRecord(new TrpGetAccountListApimDataItem(
                "DB", "DBA", "8802", "OE", 1, "T", "20/07/2026"));
        assertThat(mapped.accountEnv()).isEqualTo("DB");
        assertThat(mapped.trustCode()).isEqualTo("DBA");
        assertThat(mapped.rawPolicyNo()).isEqualTo("8802");
        assertThat(mapped.schemeType()).isEqualTo("OE");
        assertThat(mapped.rawCertNo()).isEqualTo(1);
        assertThat(mapped.termStatus()).isEqualTo("T");
        assertThat(mapped.termCompletionDate()).isEqualTo("20/07/2026");
    }

    @Test
    void preservesMalformedTransportShapeForPerRecordApplicationValidation() {
        var mapped = mapper.toRecord(new TrpGetAccountListApimDataItem(
                "", "DBA", "ABC", "OE", 0, "T", null));
        assertThat(mapped.accountEnv()).isEmpty();
        assertThat(mapped.rawPolicyNo()).isEqualTo("ABC");
        assertThat(mapped.rawCertNo()).isZero();
    }
}
