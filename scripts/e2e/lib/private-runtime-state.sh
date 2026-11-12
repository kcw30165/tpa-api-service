#!/usr/bin/env bash
__e2e_private_state_owner_declaration="$(declare -p E2E_PRIVATE_STATE_OWNED_DIR 2>/dev/null || true)"
if [[ -n "$__e2e_private_state_owner_declaration" \
  && "$__e2e_private_state_owner_declaration" != 'declare -- E2E_PRIVATE_STATE_OWNED_DIR='* ]]; then
  unset E2E_PRIVATE_STATE_OWNED_DIR
fi
unset __e2e_private_state_owner_declaration

private_runtime_state_has_ownership() {
  local declaration
  declaration="$(declare -p E2E_PRIVATE_STATE_OWNED_DIR 2>/dev/null || true)"
  [[ "$declaration" == 'declare -- E2E_PRIVATE_STATE_OWNED_DIR='* ]] || return 1
  [[ -n "${E2E_PRIVATE_STATE_OWNED_DIR:-}" \
    && -d "$E2E_PRIVATE_STATE_OWNED_DIR" \
    && ! -L "$E2E_PRIVATE_STATE_OWNED_DIR" ]]
}

private_runtime_state_is_owned() {
  private_runtime_state_has_ownership \
    && [[ "${E2E_PRIVATE_STATE_DIR:-}" == "$E2E_PRIVATE_STATE_OWNED_DIR" ]] \
    && [[ "$(stat -c '%a' "$E2E_PRIVATE_STATE_OWNED_DIR")" == 700 ]]
}

create_private_runtime_state() {
  if private_runtime_state_has_ownership; then
    destroy_private_runtime_state
  fi
  unset E2E_PRIVATE_STATE_OWNED_DIR
  umask 077
  E2E_PRIVATE_STATE_DIR="$(mktemp -d "${TMPDIR:-/tmp}/bff-e2e-private.XXXXXX")"
  chmod 700 "$E2E_PRIVATE_STATE_DIR"
  E2E_PRIVATE_STATE_OWNED_DIR="$E2E_PRIVATE_STATE_DIR"
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
  local file owned_dir
  if ! private_runtime_state_has_ownership; then
    unset E2E_PRIVATE_STATE_DIR E2E_PRIVATE_STATE_OWNED_DIR
    return 0
  fi
  owned_dir="$E2E_PRIVATE_STATE_OWNED_DIR"
  while IFS= read -r file; do
    : >"$file"
    rm -f -- "$file"
  done < <(find "$owned_dir" -type f -print)
  find "$owned_dir" -depth -type d -empty -delete
  unset E2E_PRIVATE_STATE_DIR E2E_PRIVATE_STATE_OWNED_DIR
  trap - EXIT
}
