package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.mapper;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.TrpChkCustIdApimEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimDataItem;
import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.ForgotPasswordUsernameLookupResult;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TrpChkCustIdForgotPasswordResultMapper {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(TrpChkCustIdForgotPasswordResultMapper.class);
  private static final String USERNAME_NOT_FOUND = "err.tpa.cust.id.not.registered";
  private static final String INVALID_RESPONSE_LOG =
      "event=forgot-password.username-lookup.invalid-response classification=DOWNSTREAM_INVALID_RESPONSE";

  private final Map<String, MemberIdentityType> tasIdentityTypeMapping;

  public TrpChkCustIdForgotPasswordResultMapper(
      Map<String, MemberIdentityType> tasIdentityTypeMapping) {
    this.tasIdentityTypeMapping =
        Map.copyOf(
            Objects.requireNonNull(
                tasIdentityTypeMapping, "tasIdentityTypeMapping must not be null"));
  }

  public ForgotPasswordUsernameLookupResult toResult(TrpChkCustIdApimEnvelope envelope) {
    String errMessage = envelope == null ? null : envelope.errMessage();
    if (errMessage != null && !errMessage.isBlank()) {
      boolean notFound = USERNAME_NOT_FOUND.equals(errMessage.trim());
      LOGGER.warn(
          "event=forgot-password.username-lookup.business-error classification={}",
          notFound ? "USERNAME_NOT_FOUND" : "UNRECOGNIZED_DOWNSTREAM_ERROR");
      return notFound
          ? ForgotPasswordUsernameLookupResult.notFound()
          : ForgotPasswordUsernameLookupResult.systemFailed(FailureOrigin.DOWNSTREAM_CONTRACT);
    }

    List<TrpChkCustIdApimDataItem> data =
        envelope == null || envelope.data() == null ? List.of() : envelope.data();
    if (data.size() != 1) {
      LOGGER.warn(INVALID_RESPONSE_LOG);
      return ForgotPasswordUsernameLookupResult.systemFailed(
          FailureOrigin.DOWNSTREAM_INVALID_RESPONSE);
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
      LOGGER.warn(INVALID_RESPONSE_LOG);
      return ForgotPasswordUsernameLookupResult.systemFailed(
          FailureOrigin.DOWNSTREAM_INVALID_RESPONSE);
    }

    MemberIdentityType identityType = tasIdentityTypeMapping.get(tasIdentityType);
    if (identityType == null) {
      LOGGER.warn(INVALID_RESPONSE_LOG);
      return ForgotPasswordUsernameLookupResult.systemFailed(
          FailureOrigin.DOWNSTREAM_INVALID_RESPONSE);
    }

    LOGGER.info("event=forgot-password.username-lookup.confirmed");
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
