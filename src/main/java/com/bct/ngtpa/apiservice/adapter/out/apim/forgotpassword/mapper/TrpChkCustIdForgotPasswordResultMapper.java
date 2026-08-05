package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.mapper;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.TrpChkCustIdApimEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimDataItem;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationIdentityType;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.ForgotPasswordUsernameLookupResult;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TrpChkCustIdForgotPasswordResultMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrpChkCustIdForgotPasswordResultMapper.class);
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
            boolean notFound = USERNAME_NOT_FOUND.equals(errMessage.trim());
            LOGGER.warn("event=forgot-password.username-lookup.business-error recognizedNotFound={} errorCode={}",
                    notFound, errMessage.trim());
            return notFound ? ForgotPasswordUsernameLookupResult.notFound()
                    : ForgotPasswordUsernameLookupResult.systemFailed();
        }

        List<TrpChkCustIdApimDataItem> data = envelope == null || envelope.data() == null ? List.of() : envelope.data();
        if (data.size() != 1) {
            LOGGER.error("event=forgot-password.username-lookup.invalid-response reason=unexpected-data-count dataCount={}",
                    data.size());
            return ForgotPasswordUsernameLookupResult.systemFailed();
        }

        TrpChkCustIdApimDataItem item = data.getFirst();
        if (!item.exists()) {
            LOGGER.info("event=forgot-password.username-lookup.not-found reason=isExist-false");
            return ForgotPasswordUsernameLookupResult.notFound();
        }

        String customerId = normalize(item.customerId());
        String userId = normalize(item.userId());
        String tasIdentityType = normalize(item.idType());
        if (customerId == null || userId == null || tasIdentityType == null) {
            LOGGER.error("event=forgot-password.username-lookup.invalid-response reason=missing-required-field customerIdPresent={} userIdPresent={} idTypePresent={}",
                    customerId != null, userId != null, tasIdentityType != null);
            return ForgotPasswordUsernameLookupResult.systemFailed();
        }

        RegistrationIdentityType identityType = tasIdentityTypeMapping.get(tasIdentityType);
        if (identityType == null) {
            LOGGER.error("event=forgot-password.username-lookup.invalid-response reason=unmapped-id-type tasIdentityType={} configuredTypes={}",
                    tasIdentityType, tasIdentityTypeMapping.keySet());
            return ForgotPasswordUsernameLookupResult.systemFailed();
        }

        LOGGER.info("event=forgot-password.username-lookup.confirmed tasIdentityType={} publicIdentityType={}",
                tasIdentityType, identityType == RegistrationIdentityType.STAFFNO ? "STAFF_NO" : identityType.name());
        return ForgotPasswordUsernameLookupResult.confirmed(customerId, userId, identityType, tasIdentityType);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}