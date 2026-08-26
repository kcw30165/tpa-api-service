#!/usr/bin/env bash
set -euo pipefail
run_scenario() { printf '{"status":"UP"}\n' >"$E2E_RUN_EVIDENCE_DIR/007-response-body-redacted.json"; assert_json_path_exists "$E2E_RUN_EVIDENCE_DIR/007-response-body-redacted.json" '.status'; assert_json_path_absent "$E2E_RUN_EVIDENCE_DIR/007-response-body-redacted.json" '.password'; E2E_CURL_EXIT=0; E2E_HTTP_STATUS=SELF_TEST; E2E_ELAPSED_SECONDS=0; }
