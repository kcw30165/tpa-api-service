package com.bct.ngtpa.apiservice.application.usecase.auth.login.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.login.account.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoginAccountRecordValidationServiceTest {
    private final LoginAccountRecordValidationService service =
            new LoginAccountRecordValidationService(11, 4, '0');

    @Test
    void validatesFormatsAndPreservesSourceOrderAndNullableSchemeType() {
        var result = service.validate(List.of(
                raw("DB", "DBA", "8802", "OE", 1, null, null),
                raw("JP", "JPA", "9901", null, 2, "T", "20/07/2026")));

        assertThat(result.status()).isEqualTo(LoginAccountValidationStatus.VALID);
        assertThat(result.returnedCount()).isEqualTo(2);
        assertThat(result.rejectedCount()).isZero();
        assertThat(result.accounts()).extracting(LoginValidatedAccount::accountEnv)
                .containsExactly("DB", "JP");
        assertThat(result.accounts().get(0).policyNo()).isEqualTo("00000008802");
        assertThat(result.accounts().get(0).certNo()).isEqualTo("0001");
        assertThat(result.accounts().get(1).schemeType()).isNull();
        assertThat(result.accounts().get(1).termCompletionDate())
                .isEqualTo(LocalDate.of(2026, 7, 20));
    }

    @Test
    void omitsMalformedRecordsWhenAtLeastOneValidRecordRemains() {
        var result = service.validate(List.of(
                raw("DB", "DBA", "ABC", "OE", 1, null, null),
                raw("JP", "JPA", "8802", "OE", 1, null, null),
                raw("HK", "HKA", "9", "OE", 1, "T", null)));

        assertThat(result.status()).isEqualTo(LoginAccountValidationStatus.VALID);
        assertThat(result.returnedCount()).isEqualTo(3);
        assertThat(result.rejectedCount()).isEqualTo(2);
        assertThat(result.accounts()).singleElement()
                .extracting(LoginValidatedAccount::accountEnv)
                .isEqualTo("JP");
        assertThat(result.rejections()).extracting(LoginAccountRecordRejection::category)
                .containsExactly(
                        LoginAccountRecordRejectionCategory.POLICY_NUMBER,
                        LoginAccountRecordRejectionCategory.TERMINATION_PAIR);
    }

    @Test
    void distinguishesReturnedButZeroValidRecordsFromUnavailableEnvelope() {
        var result = service.validate(List.of(
                raw("DB", "DBA", "0", "OE", 1, null, null),
                raw("JP", "JPA", "8802", "OE", -1, null, null)));

        assertThat(result.status()).isEqualTo(LoginAccountValidationStatus.ZERO_VALID_RECORDS);
        assertThat(result.accounts()).isEmpty();
        assertThat(result.returnedCount()).isEqualTo(2);
        assertThat(result.rejectedCount()).isEqualTo(2);
    }

    @Test
    void strictlyValidatesTerminationPairAndDate() {
        assertThat(service.validate(List.of(
                raw("DB", "DBA", "8802", "OE", 1, "T", "31/02/2026")))
                .status()).isEqualTo(LoginAccountValidationStatus.ZERO_VALID_RECORDS);
        assertThat(service.validate(List.of(
                raw("DB", "DBA", "8802", "OE", 1, "X", null)))
                .status()).isEqualTo(LoginAccountValidationStatus.ZERO_VALID_RECORDS);
        assertThat(service.validate(List.of(
                raw("DB", "DBA", "8802", "OE", 1, null, "20/07/2026")))
                .status()).isEqualTo(LoginAccountValidationStatus.ZERO_VALID_RECORDS);
    }

    private LoginAccountRecord raw(
            String env, String trust, String policy, String scheme,
            int cert, String termStatus, String termDate) {
        return new LoginAccountRecord(env, trust, policy, scheme, cert, termStatus, termDate);
    }
}
