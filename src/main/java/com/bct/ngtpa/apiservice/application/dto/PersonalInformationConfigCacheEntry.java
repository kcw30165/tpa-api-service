package com.bct.ngtpa.apiservice.application.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The field-access config of the selected account cached in the authenticated session, per the
 * personal-information design section 5 (D-11).
 *
 * <p>Holds only the item-id to config-value map exactly as returned by TRPGetMemberInfo, bound to
 * the account it was fetched for. No member data. An entry matches only when both {@code
 * accountRef} and {@code policyNo} equal the current account context; anything else is a cache
 * miss. A null config value is stored as blank (a blank access value is hidden, D-32a). An empty
 * map is rejected: an entry without config cannot decide field access. {@code toString()} hides
 * both identifiers.
 */
public record PersonalInformationConfigCacheEntry(
    String accountRef, String policyNo, Map<String, String> itemConfig) {

  public PersonalInformationConfigCacheEntry {
    requireText(accountRef, "accountRef");
    requireText(policyNo, "policyNo");
    Objects.requireNonNull(itemConfig, "itemConfig must not be null");
    if (itemConfig.isEmpty()) {
      throw new IllegalArgumentException("itemConfig must not be empty");
    }
    Map<String, String> copy = new LinkedHashMap<>();
    itemConfig.forEach(
        (itemId, value) -> {
          requireText(itemId, "itemConfig item id");
          copy.put(itemId, value == null ? "" : value);
        });
    itemConfig = Collections.unmodifiableMap(copy);
  }

  /** True only when both identifiers match; anything else is a cache miss. */
  public boolean belongsTo(String accountRef, String policyNo) {
    return this.accountRef.equals(accountRef) && this.policyNo.equals(policyNo);
  }

  @Override
  public String toString() {
    return "PersonalInformationConfigCacheEntry[accountRef=<redacted>, policyNo=<redacted>, itemCount="
        + itemConfig.size()
        + "]";
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
