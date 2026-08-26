#!/usr/bin/env bash
set -euo pipefail
run_scenario() { bff_request GET '/actuator/health/readiness'; assert_eq 0 "$E2E_CURL_EXIT" curl_exit; assert_eq 200 "$E2E_HTTP_STATUS" http_status; assert_json_path_exists "$E2E_RUN_EVIDENCE_DIR/007-response-body-redacted.json" '.status'; }
