#!/usr/bin/env bash
# Descriptor 199 is an in-process capability; inherited E2E copies are rejected by PID.
if [[ -d "/proc/$BASHPID/fd/199" ]]; then
  __e2e_private_state_imported_path="$(readlink "/proc/$BASHPID/fd/199" 2>/dev/null || true)"
  __e2e_private_state_imported_name="${__e2e_private_state_imported_path##*/}"
  if [[ "$__e2e_private_state_imported_name" == bff-e2e-private.* \
    && "$__e2e_private_state_imported_name" != "bff-e2e-private.$BASHPID."* ]]; then
    exec 199<&-
  fi
fi
__e2e_private_state_imported_name="${E2E_PRIVATE_STATE_DIR:-}"
__e2e_private_state_imported_name="${__e2e_private_state_imported_name##*/}"
if [[ "$__e2e_private_state_imported_name" =~ ^bff-e2e-private\.([0-9]+)\.[[:alnum:]]+$ \
  && "${BASH_REMATCH[1]}" != "$BASHPID" ]]; then
  unset E2E_PRIVATE_STATE_DIR
fi
unset __e2e_private_state_imported_path __e2e_private_state_imported_name
unset E2E_PRIVATE_STATE_OWNED_DIR 2>/dev/null || true

private_runtime_state_descriptor_path() {
  printf '/proc/%s/fd/199\n' "$1"
}

private_runtime_state_path_pid() {
  local name="${E2E_PRIVATE_STATE_DIR:-}"
  name="${name##*/}"
  [[ "$name" =~ ^bff-e2e-private\.([0-9]+)\.[[:alnum:]]+$ ]] || return 1
  printf '%s\n' "${BASH_REMATCH[1]}"
}

private_runtime_state_owned_path() {
  local shell_pid="$1" descriptor_path owned_path descriptor_identity path_identity
  descriptor_path="$(private_runtime_state_descriptor_path "$shell_pid")"
  [[ -d "$descriptor_path" ]] || return 1
  owned_path="$(readlink "$descriptor_path" 2>/dev/null)" || return 1
  [[ "$owned_path" != *" (deleted)" && -d "$owned_path" && ! -L "$owned_path" ]] || return 1
  [[ "${owned_path##*/}" == "bff-e2e-private.${shell_pid}."* ]] || return 1
  descriptor_identity="$(stat -Lc '%d:%i' "$descriptor_path" 2>/dev/null)" || return 1
  path_identity="$(stat -Lc '%d:%i' "$owned_path" 2>/dev/null)" || return 1
  [[ "$descriptor_identity" == "$path_identity" ]] || return 1
  printf '%s\n' "$owned_path"
}

private_runtime_state_has_ownership() {
  local shell_pid
  shell_pid="$(private_runtime_state_path_pid)" || return 1
  [[ "$shell_pid" == "$$" ]] || return 1
  private_runtime_state_owned_path "$shell_pid" >/dev/null
}

private_runtime_state_is_owned() {
  local shell_pid owned_path
  shell_pid="$(private_runtime_state_path_pid)" || return 1
  [[ "$shell_pid" == "$$" ]] || return 1
  owned_path="$(private_runtime_state_owned_path "$shell_pid")" || return 1
  [[ "${E2E_PRIVATE_STATE_DIR:-}" == "$owned_path" ]] \
    && [[ "$(stat -c '%a' "$owned_path" 2>/dev/null)" == 700 ]]
}

create_private_runtime_state() {
  [[ "$BASHPID" == "$$" ]] || {
    printf 'FAIL private runtime state must be initialized by the owning shell\n' >&2
    return 1
  }
  local shell_pid="$$" owned_path descriptor_path
  if private_runtime_state_has_ownership; then
    if private_runtime_state_is_owned; then
      return 0
    fi
    printf 'FAIL private runtime state is active but its path or permissions are unsafe\n' >&2
    return 1
  fi
  descriptor_path="$(private_runtime_state_descriptor_path "$shell_pid")"
  if [[ -e "$descriptor_path" ]]; then
    printf 'FAIL private runtime state descriptor 199 is already in use\n' >&2
    return 1
  fi
  umask 077
  owned_path="$(mktemp -d "${TMPDIR:-/tmp}/bff-e2e-private.${shell_pid}.XXXXXX")" || {
    printf 'FAIL could not create private runtime state directory\n' >&2
    return 1
  }
  if ! chmod 700 "$owned_path" || ! exec 199<"$owned_path"; then
    rmdir -- "$owned_path" 2>/dev/null || true
    printf 'FAIL could not initialize private runtime state directory\n' >&2
    return 1
  fi
  E2E_PRIVATE_STATE_DIR="$owned_path"
  export E2E_PRIVATE_STATE_DIR
  trap 'private_runtime_state_exit_cleanup "$?"' EXIT
}

write_private_value() {
  local name="$1" value="$2" file
  private_runtime_state_is_owned || {
    printf 'FAIL refusing to write outside owned private runtime state\n' >&2
    return 1
  }
  file="$E2E_PRIVATE_STATE_DIR/$name"
  umask 077
  printf '%s' "$value" >"$file" && chmod 600 "$file"
}

read_private_value() {
  local name="$1"
  private_runtime_state_is_owned || {
    printf 'FAIL refusing to read outside owned private runtime state\n' >&2
    return 1
  }
  cat "$E2E_PRIVATE_STATE_DIR/$name"
}

private_runtime_state_cleanup_failure() {
  printf 'FAIL private runtime state cleanup failed for %s; ownership retained for retry\n' \
    "${1:-<unknown>}" >&2
  return 1
}

destroy_private_runtime_state() {
  local shell_pid="$$" owned_path descriptor_path descriptor_identity path_identity
  if [[ "$BASHPID" != "$$" ]]; then
    shell_pid="$BASHPID"
    descriptor_path="$(private_runtime_state_descriptor_path "$shell_pid")"
    if [[ -e "$descriptor_path" ]]; then
      private_runtime_state_cleanup_failure "$descriptor_path"
      return 1
    fi
    unset E2E_PRIVATE_STATE_DIR E2E_PRIVATE_STATE_OWNED_DIR
    return 0
  fi
  descriptor_path="$(private_runtime_state_descriptor_path "$shell_pid")"
  owned_path="$(private_runtime_state_owned_path "$shell_pid")" || {
    if [[ -e "$descriptor_path" ]]; then
      private_runtime_state_cleanup_failure "$descriptor_path"
      return 1
    fi
    unset E2E_PRIVATE_STATE_DIR E2E_PRIVATE_STATE_OWNED_DIR
    return 0
  }
  descriptor_identity="$(stat -Lc '%d:%i' "$descriptor_path" 2>/dev/null)" \
    || { private_runtime_state_cleanup_failure "$descriptor_path"; return 1; }
  path_identity="$(stat -Lc '%d:%i' "$owned_path" 2>/dev/null)" \
    || { private_runtime_state_cleanup_failure "$owned_path"; return 1; }
  [[ "$descriptor_identity" == "$path_identity" && ! -L "$owned_path" ]] \
    || { private_runtime_state_cleanup_failure "$owned_path"; return 1; }

  find "$owned_path" -mindepth 1 -type l -delete \
    || { private_runtime_state_cleanup_failure "$owned_path"; return 1; }
  find "$owned_path" -mindepth 1 -type f \
    -exec sh -c 'for file; do : >"$file" && rm -f -- "$file" || exit 1; done' sh {} + \
    || { private_runtime_state_cleanup_failure "$owned_path"; return 1; }
  find "$owned_path" -mindepth 1 -depth -type d -empty -delete \
    || { private_runtime_state_cleanup_failure "$owned_path"; return 1; }

  descriptor_identity="$(stat -Lc '%d:%i' "$descriptor_path" 2>/dev/null)" \
    || { private_runtime_state_cleanup_failure "$descriptor_path"; return 1; }
  path_identity="$(stat -Lc '%d:%i' "$owned_path" 2>/dev/null)" \
    || { private_runtime_state_cleanup_failure "$owned_path"; return 1; }
  [[ "$descriptor_identity" == "$path_identity" && ! -L "$owned_path" ]] \
    || { private_runtime_state_cleanup_failure "$owned_path"; return 1; }
  rmdir -- "$owned_path" \
    || { private_runtime_state_cleanup_failure "$owned_path"; return 1; }
  [[ ! -e "$owned_path" && ! -L "$owned_path" ]] \
    || { private_runtime_state_cleanup_failure "$owned_path"; return 1; }

  exec 199<&-
  if [[ "${E2E_PRIVATE_STATE_DIR:-}" == "$owned_path" ]]; then
    unset E2E_PRIVATE_STATE_DIR
  fi
  unset E2E_PRIVATE_STATE_OWNED_DIR
  trap - EXIT
}

private_runtime_state_exit_cleanup() {
  local original_status="$1"
  if ! destroy_private_runtime_state; then
    exit 1
  fi
  exit "$original_status"
}
