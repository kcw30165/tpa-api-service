package com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchMemberApimDataItem;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupAccount;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupDownstreamException;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupResult;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupStatus;
import java.util.List;
import java.util.Objects;

/** Maps TRPSearchMember without re-filtering or reimplementing TAS eligibility decisions. */
public class TrpSearchMemberApimResultMapper {
  private static final String MEMBER_NOT_FOUND = "err.tpa.member.not.found";

  public RegistrationLookupResult toResult(
      String errMessage,
      List<TrpSearchMemberApimDataItem> data,
      RegistrationLookupCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    String error = text(errMessage);
    if (MEMBER_NOT_FOUND.equals(error)) {
      return new RegistrationLookupResult(RegistrationLookupStatus.NOT_ELIGIBLE, List.of(), null);
    }
    if (error != null) {
      throw new RegistrationLookupDownstreamException(
          RegistrationLookupDownstreamException.Reason.UNEXPECTED_ERROR, error);
    }
    List<TrpSearchMemberApimDataItem> items = data == null ? List.of() : List.copyOf(data);
    if (items.isEmpty()) {
      throw new RegistrationLookupDownstreamException(
          RegistrationLookupDownstreamException.Reason.EMPTY_DATA, null);
    }
    return new RegistrationLookupResult(
        RegistrationLookupStatus.ELIGIBLE, items.stream().map(this::toAccount).toList(), null);
  }

  private RegistrationLookupAccount toAccount(TrpSearchMemberApimDataItem item) {
    if (item == null || text(item.env()) == null || text(item.trustCode()) == null) {
      throw new RegistrationLookupDownstreamException(
          RegistrationLookupDownstreamException.Reason.INVALID_ACCOUNT_DATA, null);
    }
    return new RegistrationLookupAccount(
        item.env().trim(),
        item.trustCode().trim(),
        item.schemeType(),
        item.policyNo(),
        item.certNo(),
        item.termStatus(),
        item.termCompleteDate(),
        item.isRegistered(),
        item.customerId());
  }

  private static String text(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
