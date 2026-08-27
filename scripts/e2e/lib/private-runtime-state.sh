#!/usr/bin/env bash
create_private_runtime_state() {
  E2E_PRIVATE_STATE_DIR="$(mktemp -d "${TMPDIR:-/tmp}/bff-e2e-private.XXXXXX")"
  chmod 700 "$E2E_PRIVATE_STATE_DIR"
  export E2E_PRIVATE_STATE_DIR
  trap destroy_private_runtime_state EXIT
}
write_private_value() {
  local name value file
  name="$1"
  value="$2"
  file="$E2E_PRIVATE_STATE_DIR/$name"
  umask 077
  printf '%s' "$value" >"$file"
  chmod 600 "$file"
}
read_private_value() {
  local name
  name="$1"
  cat "$E2E_PRIVATE_STATE_DIR/$name"
}
destroy_private_runtime_state() {
  local file
  [[ -n "${E2E_PRIVATE_STATE_DIR:-}" && -d "$E2E_PRIVATE_STATE_DIR" ]] || return 0
  while IFS= read -r file; do
    : >"$file"
    rm -f -- "$file"
  done < <(find "$E2E_PRIVATE_STATE_DIR" -type f -print)
  find "$E2E_PRIVATE_STATE_DIR" -depth -type d -empty -delete
  unset E2E_PRIVATE_STATE_DIR
  trap - EXIT
}
