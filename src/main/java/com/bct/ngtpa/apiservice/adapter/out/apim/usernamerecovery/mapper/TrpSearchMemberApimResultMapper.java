package com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.mapper;

import com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.TrpSearchMemberApimDownstreamException;
import com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.dto.TrpSearchMemberApimDataItem;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupCandidate;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupResult;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure mapper from the TRPSearchMember err-message and data items to the application lookup result.
 *
 * <p>Outcome mapping follows the APIM integration design downstream matrix: member-not-found and
 * blank-success-empty data become {@code NOT_FOUND}; structurally valid data becomes {@code
 * MATCHED}; invariant violations become {@code CONTRACT_INVALID} (missing
 * env/trust-code/isRegistered, registered record without cust-id, unregistered record with cust-id,
 * duplicate env+trust-code, invalid nonblank term-complete-date). Any other nonblank err-message is
 * unclassifiable at this boundary and throws {@link TrpSearchMemberApimDownstreamException}; the
 * raw code is carried as non-message state only. A null data item is treated as a contract
 * violation, never silently dropped. No raw APIM names or sensitive values leave this package.
 */
public class TrpSearchMemberApimResultMapper {
  private static final String MEMBER_NOT_FOUND = "err.tpa.member.not.found";
  private static final DateTimeFormatter TERM_COMPLETE_DATE =
      DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

  public UsernameRecoveryLookupResult toResult(
      String errMessage, List<TrpSearchMemberApimDataItem> data) {
    String safeError = text(errMessage);
    List<TrpSearchMemberApimDataItem> items = data == null ? List.of() : data;

    if (MEMBER_NOT_FOUND.equals(safeError)) {
      return new UsernameRecoveryLookupResult(
          UsernameRecoveryLookupResult.Status.NOT_FOUND, List.of());
    }
    if (safeError != null) {
      throw new TrpSearchMemberApimDownstreamException(
          TrpSearchMemberApimDownstreamException.Reason.UNEXPECTED_ERROR, safeError);
    }
    if (items.isEmpty()) {
      return new UsernameRecoveryLookupResult(
          UsernameRecoveryLookupResult.Status.NOT_FOUND, List.of());
    }
    return mapMatched(items);
  }

  private UsernameRecoveryLookupResult mapMatched(List<TrpSearchMemberApimDataItem> items) {
    List<UsernameRecoveryLookupCandidate> candidates = new ArrayList<>();
    Set<String> seenEnvTrust = new HashSet<>();
    for (TrpSearchMemberApimDataItem item : items) {
      if (item == null) {
        return contractInvalid();
      }
      String env = text(item.env());
      String trustCode = text(item.trustCode());
      Boolean registered = item.isRegistered();
      if (env == null || trustCode == null || registered == null) {
        return contractInvalid();
      }
      if (!seenEnvTrust.add(env + "|" + trustCode)) {
        return contractInvalid();
      }
      if (!validTermCompleteDate(text(item.termCompleteDate()))) {
        return contractInvalid();
      }
      String custId = text(item.custId());
      if (registered && custId == null) {
        return contractInvalid();
      }
      if (!registered && custId != null) {
        return contractInvalid();
      }
      candidates.add(
          new UsernameRecoveryLookupCandidate(
              env, trustCode, registered, registered ? custId : null));
    }
    return new UsernameRecoveryLookupResult(
        UsernameRecoveryLookupResult.Status.MATCHED, candidates);
  }

  private static boolean validTermCompleteDate(String value) {
    if (value == null) {
      return true;
    }
    try {
      LocalDate.parse(value, TERM_COMPLETE_DATE);
      return true;
    } catch (DateTimeParseException exception) {
      return false;
    }
  }

  private static UsernameRecoveryLookupResult contractInvalid() {
    return new UsernameRecoveryLookupResult(
        UsernameRecoveryLookupResult.Status.CONTRACT_INVALID, List.of());
  }

  private static String text(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
