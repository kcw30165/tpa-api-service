package com.bct.ngtpa.apiservice.application.usecase.auth.login.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.auth.login.account.*;
import com.bct.ngtpa.apiservice.application.port.out.LoginAccountAuthorizationPort;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GetLoginAccountsUseCaseTest {

    @Test
    void delegatesExactProtectedContextValuesWithoutInventingRequestFields() {
        AtomicReference<LoginAccountAuthorizationCommand> captured = new AtomicReference<>();
        LoginAccountAuthorizationPort port = command -> {
            captured.set(command);
            return Mono.just(LoginAccountAuthorizationResult.available(List.of(
                    new LoginAccountRecord("DB", "DBA", "8802", "OE", 1, "T", "20/07/2026"))));
        };
        var useCase = new GetLoginAccountsUseCase(port);

        StepVerifier.create(useCase.getAccounts(new GetLoginAccountsCommand(
                        "member01", "MBR", "req-1")))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(LoginAccountAuthorizationStatus.AVAILABLE);
                    assertThat(result.accounts()).hasSize(1);
                })
                .verifyComplete();

        assertThat(captured.get()).isEqualTo(new LoginAccountAuthorizationCommand(
                "member01", "MBR", "cust-id", "member01", "req-1"));
    }

    @Test
    void commandAndRecordsRejectMalformedRequiredTransportNeutralValues() {
        assertThatThrownBy(() -> new GetLoginAccountsCommand(" ", "MBR", "req"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginAccountRecord(
                        "DB", "DBA", "8802", "OE", 0, "", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unavailableOutcomeCarriesNoRawDownstreamCodeOrRecords() {
        var result = LoginAccountAuthorizationResult.unavailable();
        assertThat(result.status()).isEqualTo(LoginAccountAuthorizationStatus.UNAVAILABLE);
        assertThat(result.accounts()).isEmpty();
        assertThat(result.toString()).doesNotContain("err.tpa", "member01", "8802");
    }
}
