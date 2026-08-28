#!/usr/bin/env bash
assert_eq() { local expected="$1" actual="$2" label="$3"; [[ "$actual" == "$expected" ]] || { printf 'FAIL %s expected=%s actual=%s\n' "$label" "$expected" "$actual"; return 1; }; printf 'PASS %s\n' "$label"; }
assert_json_path_exists() { jq -e "$2 | select(. != null)" "$1" >/dev/null || { printf 'FAIL json path %s\n' "$2"; return 1; }; printf 'PASS json path %s\n' "$2"; }
assert_json_path_absent() { jq -e "$2 == null" "$1" >/dev/null || { printf 'FAIL forbidden json path %s\n' "$2"; return 1; }; printf 'PASS forbidden json path %s\n' "$2"; }

assert_json_scalar_eq() {
  local file="$1" path="$2" expected="$3" label="$4" actual
  actual="$(jq -er "$path" "$file")" || { printf 'FAIL %s missing path=%s\n' "$label" "$path"; return 1; }
  assert_eq "$expected" "$actual" "$label"
}
assert_file_not_matches() {
  local file="$1" pattern="$2" label="$3"
  if grep -Eqi "$pattern" "$file"; then printf 'FAIL %s\n' "$label"; return 1; fi
  printf 'PASS %s\n' "$label"
}
