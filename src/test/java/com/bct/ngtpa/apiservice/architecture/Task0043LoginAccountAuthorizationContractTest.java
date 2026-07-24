package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.login.account.*;
import com.bct.ngtpa.apiservice.application.port.out.LoginAccountAuthorizationPort;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043LoginAccountAuthorizationContractTest {

    @Test
    void requestContractMatchesTrpGetAccountListExactly() {
        var command = new LoginAccountAuthorizationCommand(
                "member01", "MBR", "cust-id", "member01", "req-1");
        assertThat(command.custId()).isEqualTo("member01");
        assertThat(command.custType()).isEqualTo("MBR");
        assertThat(command.searchKey()).isEqualTo("cust-id");
        assertThat(command.searchValue()).isEqualTo("member01");
        assertThat(command.toString()).doesNotContain("member01");
    }

    @Test
    void responseContractMatchesRawAccountItemWithoutPrematureFormatting() {
        var record = new LoginAccountRecord("DB", "DBA", "8802", "OE", 1, "T", "20/07/2026");
        assertThat(record.accountEnv()).isEqualTo("DB");
        assertThat(record.trustCode()).isEqualTo("DBA");
        assertThat(record.rawPolicyNo()).isEqualTo("8802");
        assertThat(record.schemeType()).isEqualTo("OE");
        assertThat(record.rawCertNo()).isEqualTo(1);
        assertThat(record.termStatus()).isEqualTo("T");
        assertThat(record.termCompletionDate()).isEqualTo("20/07/2026");
        assertThat(record.toString()).doesNotContain("8802", "DBA", "20/07/2026");
    }

    @Test
    void applicationContractIsIndependentOfApimSpringRedisCryptoAndWeb() throws Exception {
        String port = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/port/out/LoginAccountAuthorizationPort.java"));
        String dto = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/login/account/LoginAccountRecord.java"));
        assertThat(port + dto).doesNotContain(
                "adapter.out", "adapter.in", "org.springframework", "WebClient",
                "Redis", "ObjectMapper", "Cipher", "response", "err-message");
        assertThat(LoginAccountAuthorizationPort.class).isInterface();
    }

    @Test
    void accountListContractDoesNotContainAccountRefOrSessionCreation() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/login/account/LoginAccountRecord.java"));
        assertThat(source).doesNotContain(
                "accountRef", "portalSession", "authFlowSession", "csrfToken");
    }
}
