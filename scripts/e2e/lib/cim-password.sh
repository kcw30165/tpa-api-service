#!/usr/bin/env bash
acquire_cim_password() {
  [[ -n "${CIM_PASSWORD:-}" ]] && return 0
  if [[ -n "${LOGIN_TEST_PASSWORD:-}" ]]; then
    CIM_PASSWORD="$LOGIN_TEST_PASSWORD"
    return 0
  fi
  read_secret_if_missing CIM_PASSWORD 'Test member password: '
}
