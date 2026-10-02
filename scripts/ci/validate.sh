#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

for command_name in java mvn find awk; do
  command -v "$command_name" >/dev/null 2>&1 || {
    printf 'ERROR: required command unavailable: %s\n' "$command_name" >&2
    exit 1
  }
done

JAVA_MAJOR="$(java -version 2>&1 | awk -F '[".]' '/version/ {print $2; exit}')"
[[ "$JAVA_MAJOR" == "21" ]] || {
  printf 'ERROR: Java 21 is required; detected major version %s.\n' "${JAVA_MAJOR:-unknown}" >&2
  exit 1
}

MAVEN_VERSION="$(mvn -v | awk '/Apache Maven/ {print $3; exit}')"
[[ "$MAVEN_VERSION" == 3.9.* ]] || {
  printf 'ERROR: Maven 3.9.x is required; detected %s.\n' "${MAVEN_VERSION:-unknown}" >&2
  exit 1
}

MAVEN_ARGS=(-B -ntp -Dstyle.color=never)

printf 'CI_STAGE formatting\n'
mvn "${MAVEN_ARGS[@]}" spotless:check

printf 'CI_STAGE production_compile\n'
mvn "${MAVEN_ARGS[@]}" -DskipTests compile

printf 'CI_STAGE test_compile\n'
mvn "${MAVEN_ARGS[@]}" -DskipTests test-compile

printf 'CI_STAGE unit_tests_and_coverage\n'
mvn "${MAVEN_ARGS[@]}" test jacoco:report

REPORT_DIR="target/surefire-reports"
COVERAGE_XML="target/site/jacoco/jacoco.xml"
[[ -d "$REPORT_DIR" ]] || {
  printf 'ERROR: Surefire report directory is missing.\n' >&2
  exit 1
}
REPORT_COUNT="$(find "$REPORT_DIR" -maxdepth 1 -type f -name 'TEST-*.xml' | wc -l)"
[[ "$REPORT_COUNT" -gt 0 ]] || {
  printf 'ERROR: no Surefire XML reports were produced.\n' >&2
  exit 1
}
[[ -s "$COVERAGE_XML" ]] || {
  printf 'ERROR: JaCoCo XML report is missing or empty.\n' >&2
  exit 1
}

printf 'CI_VALIDATION PASS\n'
printf 'surefire_xml_count=%s\n' "$REPORT_COUNT"
printf 'surefire_pattern=%s\n' 'target/surefire-reports/TEST-*.xml'
printf 'jacoco_xml=%s\n' "$COVERAGE_XML"
