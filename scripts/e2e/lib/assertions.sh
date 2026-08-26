#!/usr/bin/env bash
assert_eq() { local expected="$1" actual="$2" label="$3"; [[ "$actual" == "$expected" ]] || { printf 'FAIL %s expected=%s actual=%s\n' "$label" "$expected" "$actual"; return 1; }; printf 'PASS %s\n' "$label"; }
assert_json_path_exists() { jq -e "$2 | select(. != null)" "$1" >/dev/null || { printf 'FAIL json path %s\n' "$2"; return 1; }; printf 'PASS json path %s\n' "$2"; }
assert_json_path_absent() { jq -e "$2 == null" "$1" >/dev/null || { printf 'FAIL forbidden json path %s\n' "$2"; return 1; }; printf 'PASS forbidden json path %s\n' "$2"; }
