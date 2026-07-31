package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.mapper;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.TrpChkCustIdApimEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimDataItem;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationIdentityType;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.ForgotPasswordUsernameLookupResult;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TrpChkCustIdForgotPasswordResultMapper {
    private static final String USERNAME_NOT_FOUND = "err.tpa.cust.id.not.registered";

    private final Map<String, RegistrationIdentityType> tasIdentityTypeMapping;

    public TrpChkCustIdForgotPasswordResultMapper(Map<String, RegistrationIdentityType> tasIdentityTypeMapping) {
        this.tasIdentityTypeMapping = Map.copyOf(Objects.requireNonNull(
                tasIdentityTypeMapping,
                "tasIdentityTypeMapping must not be null"));
    }

    public ForgotPasswordUsernameLookupResult toResult(TrpChkCustIdApimEnvelope envelope) {
        String errMessage = envelope == null ? null : envelope.errMessage();
        if (errMessage != null && !errMessage.isBlank()) {
            return USERNAME_NOT_FOUND.equals(errMessage.trim())
                    ? ForgotPasswordUsernameLookupResult.notFound()
                    : ForgotPasswordUsernameLookupResult.systemFailed();
        }

        List<TrpChkCustIdApimDataItem> data = envelope == null || envelope.data() == null ? List.of() : envelope.data();
        if (data.size() != 1) {
            return ForgotPasswordUsernameLookupResult.systemFailed();
        }

        TrpChkCustIdApimDataItem item = data.getFirst();
        if (!item.exists()) {
            return ForgotPasswordUsernameLookupResult.notFound();
        }

        String customerId = normalize(item.customerId());
        String userId = normalize(item.userId());
        String tasIdentityType = normalize(item.idType());
        if (customerId == null || userId == null || tasIdentityType == null) {
            return ForgotPasswordUsernameLookupResult.systemFailed();
        }

        RegistrationIdentityType identityType = tasIdentityTypeMapping.get(tasIdentityType);
        if (identityType == null) {
            return ForgotPasswordUsernameLookupResult.systemFailed();
        }

        return ForgotPasswordUsernameLookupResult.confirmed(customerId, userId, identityType);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}