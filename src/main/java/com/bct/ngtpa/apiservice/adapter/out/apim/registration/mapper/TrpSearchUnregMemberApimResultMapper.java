package com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchUnregMemberApimDataItem;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupAccount;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupDownstreamException;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupResult;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupStatus;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TrpSearchUnregMemberApimResultMapper {
  private static final String MEMBER_NOT_FOUND = "err.tpa.member.not.found";

  public RegistrationLookupResult toResult(
      String errMessage,
      List<TrpSearchUnregMemberApimDataItem> data,
      RegistrationLookupCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    String safeError = text(errMessage);
    List<TrpSearchUnregMemberApimDataItem> items = data == null ? List.of() : List.copyOf(data);

    if (MEMBER_NOT_FOUND.equals(safeError)) {
      return new RegistrationLookupResult(RegistrationLookupStatus.NOT_ELIGIBLE, List.of(), null);
    }
    if (safeError != null) {
      throw new RegistrationLookupDownstreamException(
          RegistrationLookupDownstreamException.Reason.UNEXPECTED_ERROR, safeError);
    }
    if (items.isEmpty()) {
      throw new RegistrationLookupDownstreamException(
          RegistrationLookupDownstreamException.Reason.EMPTY_DATA, null);
    }
    for (TrpSearchUnregMemberApimDataItem item : items) {
      validateItem(item, command.contactType(), command.contactValue());
    }
    return new RegistrationLookupResult(
        RegistrationLookupStatus.ELIGIBLE, items.stream().map(this::toAccount).toList(), null);
  }

  private void validateItem(
      TrpSearchUnregMemberApimDataItem item,
      RegistrationContactType contactType,
      String contactValue) {

    if (item == null || text(item.env()) == null || text(item.trustCode()) == null) {
      throw new RegistrationLookupDownstreamException(
          RegistrationLookupDownstreamException.Reason.INVALID_ACCOUNT_DATA, null);
    }

    String normalizedRequestContact =
        contactType == RegistrationContactType.EMAIL
            ? normalizeEmail(contactValue)
            : normalizeMobile(contactValue);

    String normalizedResponseContact =
        contactType == RegistrationContactType.EMAIL
            ? normalizeEmail(item.email())
            : normalizeMobile(item.mobileNumber());

    boolean matches = normalizedResponseContact.equals(normalizedRequestContact);

    if (!matches) {
      throw new RegistrationLookupDownstreamException(
          RegistrationLookupDownstreamException.Reason.CONTACT_MISMATCH, null);
    }
  }

  private String maskContact(RegistrationContactType contactType, String value) {

    if (value == null || value.isBlank()) {
      return "<blank>";
    }

    if (contactType == RegistrationContactType.EMAIL) {
      int atIndex = value.indexOf('@');

      if (atIndex <= 0) {
        return "<invalid-email>";
      }

      String localPart = value.substring(0, atIndex);
      String domain = value.substring(atIndex);

      String maskedLocalPart =
          localPart.length() == 1
              ? "*"
              : localPart.charAt(0) + "***" + localPart.charAt(localPart.length() - 1);

      return maskedLocalPart + domain;
    }

    if (value.length() <= 4) {
      return "****";
    }

    return "****" + value.substring(value.length() - 4);
  }

  private RegistrationLookupAccount toAccount(TrpSearchUnregMemberApimDataItem item) {
    return new RegistrationLookupAccount(
        item.email(), item.mobileNumber(), item.env().trim(), item.trustCode().trim());
  }

  private String normalizeEmail(String value) {
    String normalized = text(value);
    return normalized == null ? "" : normalized.toLowerCase(Locale.ROOT);
  }

  private String normalizeMobile(String value) {
    String normalized = text(value);
    return normalized == null ? "" : normalized;
  }

  private String text(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
