package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.TrpChkCustIdApimEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimDataItem;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationIdentityType;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.ForgotPasswordUsernameLookupStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TrpChkCustIdForgotPasswordResultMapperTest {
    private final TrpChkCustIdForgotPasswordResultMapper mapper = new TrpChkCustIdForgotPasswordResultMapper(
            Map.of(
                    "GPN", RegistrationIdentityType.GPN,
                    "GPFS", RegistrationIdentityType.GPFS,
                    "STAFFNO", RegistrationIdentityType.STAFFNO,
                    "ID", RegistrationIdentityType.ID));

    @Test
    void mapsIsExistTrueWithRequiredFieldsToConfirmedLookup() {
        var result = mapper.toResult(new TrpChkCustIdApimEnvelope(
                "",
                java.util.List.of(new TrpChkCustIdApimDataItem("db01", true, "DBA", "3010008", "STAFFNO"))));

        assertThat(result.status()).isEqualTo(ForgotPasswordUsernameLookupStatus.CONFIRMED);
        assertThat(result.customerId()).isEqualTo("db01");
        assertThat(result.userId()).isEqualTo("3010008");
        assertThat(result.identityType()).isEqualTo(RegistrationIdentityType.STAFFNO);
        assertThat(result.toString()).doesNotContain("db01", "3010008");
    }

    @Test
    void ignoresSchemeCodeForConfirmedIdentityMapping() {
        var result = mapper.toResult(new TrpChkCustIdApimEnvelope(
                "",
                java.util.List.of(new TrpChkCustIdApimDataItem("db01", true, null, "3010008", "ID"))));

        assertThat(result.status()).isEqualTo(ForgotPasswordUsernameLookupStatus.CONFIRMED);
        assertThat(result.identityType()).isEqualTo(RegistrationIdentityType.ID);
    }

    @Test
    void mapsRecognizedNotFoundOutcomesToNotFound() {
        var byFlag = mapper.toResult(new TrpChkCustIdApimEnvelope(
                "",
                java.util.List.of(new TrpChkCustIdApimDataItem("db01", false, null, null, null))));
        var byBusinessError = mapper.toResult(new TrpChkCustIdApimEnvelope(
                "err.tpa.cust.id.not.registered",
                java.util.List.of()));

        assertThat(byFlag.status()).isEqualTo(ForgotPasswordUsernameLookupStatus.NOT_FOUND);
        assertThat(byBusinessError.status()).isEqualTo(ForgotPasswordUsernameLookupStatus.NOT_FOUND);
    }

    @Test
    void mapsBlankUnsupportedAndMalformedSuccessPayloadsToSystemFailure() {
        assertThat(mapper.toResult(new TrpChkCustIdApimEnvelope(
                "",
                java.util.List.of(new TrpChkCustIdApimDataItem("db01", true, "DBA", "3010008", " ")))).status())
                .isEqualTo(ForgotPasswordUsernameLookupStatus.SYSTEM_FAILED);

        assertThat(mapper.toResult(new TrpChkCustIdApimEnvelope(
                "",
                java.util.List.of(new TrpChkCustIdApimDataItem("db01", true, "DBA", "3010008", "PASSPORT")))).status())
                .isEqualTo(ForgotPasswordUsernameLookupStatus.SYSTEM_FAILED);

        assertThat(mapper.toResult(new TrpChkCustIdApimEnvelope(
                "",
                java.util.List.of(new TrpChkCustIdApimDataItem("db01", true, "DBA", "3010008", "staff")))).status())
                .isEqualTo(ForgotPasswordUsernameLookupStatus.SYSTEM_FAILED);

        assertThat(mapper.toResult(new TrpChkCustIdApimEnvelope(
                "",
                java.util.List.of(new TrpChkCustIdApimDataItem(null, true, "DBA", "3010008", "ID")))).status())
                .isEqualTo(ForgotPasswordUsernameLookupStatus.SYSTEM_FAILED);

        assertThat(mapper.toResult(new TrpChkCustIdApimEnvelope(
                "",
                java.util.List.of(new TrpChkCustIdApimDataItem("db01", true, "DBA", null, "ID")))).status())
                .isEqualTo(ForgotPasswordUsernameLookupStatus.SYSTEM_FAILED);

        assertThat(mapper.toResult(new TrpChkCustIdApimEnvelope(
                "",
                java.util.List.of(
                        new TrpChkCustIdApimDataItem("db01", true, "DBA", "3010008", "ID"),
                        new TrpChkCustIdApimDataItem("db02", true, "DBA", "3010009", "ID"))))
                .status())
                .isEqualTo(ForgotPasswordUsernameLookupStatus.SYSTEM_FAILED);
    }

    @Test
    void mapsUnknownBusinessErrorsToSystemFailure() {
        var result = mapper.toResult(new TrpChkCustIdApimEnvelope("err.tpa.some.unknown.error", java.util.List.of()));

        assertThat(result.status()).isEqualTo(ForgotPasswordUsernameLookupStatus.SYSTEM_FAILED);
    }
}