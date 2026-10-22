package com.bct.ngtpa.apiservice.shared.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Group-wide field-access normalization (D-19, sub-task 3.1b).
 *
 * <p>Within a field group, any non-READONLY field makes the whole group non-READONLY, and any
 * REQUIRED field makes the whole group REQUIRED. A group whose member fields disagree before
 * normalization is logged as an inconsistency at ERROR level (group name and item ids only, no
 * member data).
 *
 * <p>Operates only on item ids already present in {@code readonlyByItemId} / {@code
 * requiredByItemId} -- i.e. fields that survived upstream HIDDEN filtering and YAML binding. Per
 * D-24, a HIDDEN field is not visible to this normalizer (it was already filtered out of
 * fieldStates before this point), so a HIDDEN-field-vs-non-HIDDEN-disagreement case as described in
 * D-24 cannot be detected at this integration point; this is a known, flagged limitation (see
 * design doc), not a silent gap.
 */
public final class FieldGroupAccessNormalizer {

  private static final Logger LOGGER = LoggerFactory.getLogger(FieldGroupAccessNormalizer.class);

  private FieldGroupAccessNormalizer() {}

  public record Result(
      Map<String, Boolean> readonlyByItemId,
      Map<String, Boolean> requiredByItemId,
      boolean inconsistent) {}

  public static Result normalize(
      String groupName,
      List<String> groupItemIds,
      Map<String, Boolean> readonlyByItemId,
      Map<String, Boolean> requiredByItemId) {

    List<String> presentItemIds =
        groupItemIds.stream()
            .filter(
                itemId ->
                    readonlyByItemId.containsKey(itemId) && requiredByItemId.containsKey(itemId))
            .toList();

    if (presentItemIds.size() < 2) {
      Map<String, Boolean> readonlyOut = new HashMap<>();
      Map<String, Boolean> requiredOut = new HashMap<>();
      for (String itemId : presentItemIds) {
        readonlyOut.put(itemId, readonlyByItemId.get(itemId));
        requiredOut.put(itemId, requiredByItemId.get(itemId));
      }
      return new Result(readonlyOut, requiredOut, false);
    }

    boolean allReadonly = presentItemIds.stream().allMatch(readonlyByItemId::get);
    boolean anyRequired = presentItemIds.stream().anyMatch(requiredByItemId::get);

    boolean readonlyDisagreement =
        presentItemIds.stream().map(readonlyByItemId::get).distinct().count() > 1;
    boolean requiredDisagreement =
        presentItemIds.stream().map(requiredByItemId::get).distinct().count() > 1;
    boolean inconsistent = readonlyDisagreement || requiredDisagreement;

    if (inconsistent) {
      LOGGER.error(
          "Field group access inconsistency detected: group={}, itemIds={}, "
              + "readonlyDisagreement={}, requiredDisagreement={}",
          groupName,
          presentItemIds,
          readonlyDisagreement,
          requiredDisagreement);
    }

    Map<String, Boolean> readonlyOut = new HashMap<>();
    Map<String, Boolean> requiredOut = new HashMap<>();
    for (String itemId : presentItemIds) {
      readonlyOut.put(itemId, allReadonly);
      requiredOut.put(itemId, anyRequired);
    }
    return new Result(readonlyOut, requiredOut, inconsistent);
  }
}
