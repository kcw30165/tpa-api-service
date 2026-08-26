#!/usr/bin/env bash
set -euo pipefail
bootstrap_context() { printf 'mode=anonymous\nsession=<not-provided>\n' >"$E2E_RUN_EVIDENCE_DIR/003-bootstrap-summary-redacted.txt"; }
