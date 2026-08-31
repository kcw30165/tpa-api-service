#!/usr/bin/env bash
set -euo pipefail

fail_assertion(){ printf 'ASSERTION_FAILED scenario=%s assertion=%s\n' "$SCENARIO" "$1" >&2; return 1; }
assert_status(){ [[ "$HTTP_STATUS" == "$1" ]] || fail_assertion "status_expected_$1_actual_$HTTP_STATUS"; }
assert_json(){ local expression="$1"; python - "$RESPONSE_FILE" "$expression" <<'PY'
import json,sys
path,expression=sys.argv[1:]
data=json.load(open(path,encoding='utf-8'))
current=data
for part in expression.split('.'):
    if not part: continue
    if isinstance(current,list): current=current[int(part)]
    elif isinstance(current,dict) and part in current: current=current[part]
    else: raise SystemExit(1)
if current in (None,False,"",[],{}): raise SystemExit(1)
PY
}
assert_json_equals(){ local expression="$1" expected="$2"; python - "$RESPONSE_FILE" "$expression" "$expected" <<'PY'
import json,sys
path,expression,expected=sys.argv[1:]
data=json.load(open(path,encoding='utf-8'))
current=data
for part in expression.split('.'):
    current=current[int(part)] if isinstance(current,list) else current[part]
if str(current).lower()!=expected.lower(): raise SystemExit(1)
PY
}
assert_json_absent_recursive(){ local key="$1"; python - "$RESPONSE_FILE" "$key" <<'PY'
import json,sys
data=json.load(open(sys.argv[1],encoding='utf-8')); key=sys.argv[2]
def present(v):
    if isinstance(v,dict): return key in v or any(present(x) for x in v.values())
    if isinstance(v,list): return any(present(x) for x in v)
    return False
if present(data): raise SystemExit(1)
PY
}
