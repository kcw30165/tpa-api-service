# Milestone 4, sub-task 4.1 -- Regression proof

Per docs/design/13-fund-comparison/07-implementation-plan.md section 7.5: diff-based
proof (not just a claim) that `FetchFundPriceRangeCommand`, `FundPriceSourcePort`,
`ApimFundPriceSourceAdapter`, and every existing Fund Price public response remain
byte-for-byte unchanged on this branch, relative to the confirmed real merge-base
with `origin/dev`.

## Confirmed merge-base

```
9e7a85aea6df21d39179cf0b10e2a5d7d2ff7269
2026-10-15 06:55:45 +0000 Merge branch 'feature/fund-price-fund-list-request-field' into 'dev'
```

## Zero-diff confirmation (re-verified, re-executable at any time)

The following 8 files were diffed against the confirmed merge-base and shown to
have **zero diff** -- byte-for-byte unchanged:

1. `application/dto/fundprice/FetchFundPriceRangeCommand.java`
2. `application/dto/fundprice/FetchFundPriceCommand.java`
3. `application/port/out/FundPriceSourcePort.java`
4. `adapter/in/web/fundprice/response/FundPriceModeResponse.java`
5. `adapter/in/web/fundprice/response/FundPriceModeResultResponse.java`
6. `adapter/in/web/fundprice/response/FundPriceQueryResponse.java`
7. `adapter/in/web/fundprice/response/FundPriceQueryResultResponse.java`
8. `adapter/in/web/fundprice/response/FundPriceRowResponse.java`

## Whole-footprint confirmation

A `git diff --name-status` across every file anywhere under a `fundprice` path
(main and test sources) confirms **exactly 2 files changed** on this branch, and no
others:

```
M	src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/fundprice/ApimFundPriceSourceAdapter.java
M	src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/fundprice/ApimFundPriceSourceAdapterTest.java
```

## The one confirmed change: `ApimFundPriceSourceAdapter.java`

This file was additively extended in Milestone 3, sub-task 3.1 (corrected design) to
also implement `FundComparisonPriceSourcePort`, reusing the same downstream wire
integration rather than duplicating it in a separate adapter class. The change is
confirmed, by direct diff review, to be purely additive:

- A new `fetchPrices(FetchFundComparisonPricesCommand)` method was added.
- The private `exchangeAndReconcile(...)` helper gained a `fundList` parameter
  (previously a hardcoded `""` literal inline).
- `fetchPrice(...)` and `fetchPriceRange(...)` now pass `""` **explicitly** at
  their own call sites -- their resulting behavior is byte-for-byte identical to
  before, confirmed both by this diff (no change to the values passed into the
  downstream request) and independently by a behavioral test harness (sub-task 3.1,
  script 057) proving `fetchPrice`/`fetchPriceRange` still send `fund-list=""`.
- No existing method signature was removed, renamed, or given different public
  semantics.

Full diff:

```diff
diff --git a/src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/fundprice/ApimFundPriceSourceAdapter.java b/src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/fundprice/ApimFundPriceSourceAdapter.java
index ce99a598..e27469b2 100644
--- a/src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/fundprice/ApimFundPriceSourceAdapter.java
+++ b/src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/fundprice/ApimFundPriceSourceAdapter.java
@@ -7,10 +7,13 @@ import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
 import com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto.TrpGetFundPriceApimDataItem;
 import com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto.TrpGetFundPriceApimRequest;
 import com.bct.ngtpa.apiservice.adapter.out.apim.retry.ApimRetryEligibility;
+import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
+import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonPricesCommand;
 import com.bct.ngtpa.apiservice.application.dto.fundprice.FetchFundPriceCommand;
 import com.bct.ngtpa.apiservice.application.dto.fundprice.FetchFundPriceRangeCommand;
 import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceData;
 import com.bct.ngtpa.apiservice.application.exception.FundPriceRetrievalException;
+import com.bct.ngtpa.apiservice.application.port.out.FundComparisonPriceSourcePort;
 import com.bct.ngtpa.apiservice.application.port.out.FundPriceSourcePort;
 import java.time.LocalDate;
 import java.time.format.DateTimeFormatter;
@@ -34,9 +37,20 @@ import reactor.core.publisher.Mono;
  * this adapter's downstream request shape (corrected, Nick: an earlier version of this adapter
  * incorrectly expanded MONTHLY into a first/last-calendar-day range; there is no such expansion).
  * Mode exists solely so FE can choose the correct picker UI.
+ *
+ * <p>Additively extended (docs/design/13-fund-comparison/07-implementation-plan.md section 7.4,
+ * corrected sub-task 3.1, script 057): also implements {@link FundComparisonPriceSourcePort} so
+ * Fund Comparison's prices flow can reuse this SAME downstream wire integration (same
+ * request/response DTOs, same {@link FundPriceApimContentValidator} reconciliation, same error
+ * mapping) rather than duplicating it in a separate adapter class. {@link #fetchPrice} and {@link
+ * #fetchPriceRange}'s own behavior is byte-for-byte unchanged -- both still send {@code
+ * fund-list=""} exactly as before, now explicitly at their own call sites instead of via a single
+ * hardcoded literal inside {@link #exchangeAndReconcile}, which now accepts {@code fundList} as a
+ * parameter.
  */
 @Component
-public final class ApimFundPriceSourceAdapter implements FundPriceSourcePort {
+public final class ApimFundPriceSourceAdapter
+    implements FundPriceSourcePort, FundComparisonPriceSourcePort {
 
   private final ApimExchangeExecutor executor;
   private final String path;
@@ -64,29 +78,43 @@ public final class ApimFundPriceSourceAdapter implements FundPriceSourcePort {
   public Mono<RawFundPriceData> fetchPrice(FetchFundPriceCommand command) {
     Objects.requireNonNull(command, "command must not be null");
 
-    LocalDate fromDate;
-    LocalDate toDate;
     // MONTHLY does not differ from DAILY in downstream range derivation (corrected,
     // Nick): FE always submits one exact validated date regardless of mode; Java sends
     // that same date as both from-date and to-date. mode has no effect on this
     // adapter's downstream request shape and exists only so FE can choose the correct
     // picker UI.
-    fromDate = command.date();
-    toDate = command.date();
+    LocalDate fromDate = command.date();
+    LocalDate toDate = command.date();
 
     return exchangeAndReconcile(
-        command.policyNo(), fromDate, toDate, command.accountEnv(), command.actorUserRole());
+        command.policyNo(), fromDate, toDate, "", command.accountEnv(), command.actorUserRole());
   }
 
   @Override
   public Mono<RawFundPriceData> fetchPriceRange(FetchFundPriceRangeCommand command) {
     Objects.requireNonNull(command, "command must not be null");
-    // NO mode logic at all -- the explicit fromDate/toDate range is passed straight through,
-    // confirmed design (export has no mode concept).
+    // NO mode logic at all -- the explicit fromDate/toDate range is passed straight
+    // through, confirmed design (export has no mode concept).
+    return exchangeAndReconcile(
+        command.policyNo(),
+        command.fromDate(),
+        command.toDate(),
+        "",
+        command.accountEnv(),
+        command.actorUserRole());
+  }
+
+  @Override
+  public Mono<RawFundPriceData> fetchPrices(FetchFundComparisonPricesCommand command) {
+    Objects.requireNonNull(command, "command must not be null");
+    // Fund Comparison's own command already carries the real, comma-joined fund-code
+    // string (joined by the use case, confirmed design) -- passed straight through to
+    // the same shared exchange-and-reconcile path fetchPrice/fetchPriceRange use.
     return exchangeAndReconcile(
         command.policyNo(),
         command.fromDate(),
         command.toDate(),
+        command.fundList(),
         command.accountEnv(),
         command.actorUserRole());
   }
@@ -95,18 +123,12 @@ public final class ApimFundPriceSourceAdapter implements FundPriceSourcePort {
       String policyNo,
       LocalDate fromDate,
       LocalDate toDate,
+      String fundList,
       String accountEnv,
-      com.bct.ngtpa.apiservice.application.dto.ActorUserRole actorUserRole) {
+      ActorUserRole actorUserRole) {
     var request =
         new TrpGetFundPriceApimRequest(
-            policyNo,
-            fromDate.format(formatter),
-            toDate.format(formatter),
-            // fund-list is always "" for all current (non-comparison) usages -- confirmed
-            // design, docs/design/9-fund-price/fund-price-decisions-open-questions.md
-            // (2026-10-15). A later fund-comparison feature will populate this with a
-            // comma-joined FE-supplied fund-code string.
-            "");
+            policyNo, fromDate.format(formatter), toDate.format(formatter), fundList);
     var exchange =
         new ApimExchangeRequest<>(
             path,
```

## Test-suite confirmation

The full Fund Price test sweep (every test class matching `*FundPrice*`, not just
the single adapter test) passes cleanly against this change.

## Conclusion

Sub-task 4.1 is satisfied: every Fund Price public contract and response named in
the design plan remains byte-for-byte unchanged, and the one confirmed change
(`ApimFundPriceSourceAdapter.java`) is purely additive with zero observable
behavioral regression, confirmed by both diff review and the full Fund Price test
suite.
