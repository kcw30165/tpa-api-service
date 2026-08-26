#!/usr/bin/env bash
set -euo pipefail
create_evidence_dir() {
  local ts scenario
  ts="$(date +%Y%m%d-%H%M%S)"; scenario="${E2E_SCENARIO_ID//[^A-Za-z0-9._-]/-}"
  E2E_RUN_EVIDENCE_DIR="${E2E_EVIDENCE_ROOT:-target/evidence}/${ts}-bff-e2e-${E2E_SCENARIO_PACK}-${scenario}"
  mkdir -p "$E2E_RUN_EVIDENCE_DIR"; chmod 700 "$E2E_RUN_EVIDENCE_DIR"; export E2E_RUN_EVIDENCE_DIR
}
