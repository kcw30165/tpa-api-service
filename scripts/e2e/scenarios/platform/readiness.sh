#!/usr/bin/env bash
run_scenario() {
  local rc=0
  bff_request GET '/actuator/health/readiness'
  assert_eq 0 "$E2E_CURL_EXIT" curl_exit || rc=1
  assert_eq 200 "$E2E_HTTP_STATUS" http_status || rc=1
  assert_json_path_exists "$E2E_RUN_EVIDENCE_DIR/007-response-body-redacted.json" '.status' || rc=1
  return "$rc"
}
