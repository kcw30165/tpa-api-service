# Phase 04: Direct Spring Boot Runtime and File Logs

## Objective

Provide a fast WSL-native application runtime with controlled startup, health checking, shutdown, and durable file-based logs.

## Runtime command

The underlying application command remains:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

A runtime controller will wrap this command.

## Runtime controller responsibilities

- Load non-secret tool configuration.
- Load secret runtime values from a protected file without printing them.
- Verify Redis forwarded ports.
- Verify required Redis TLS files.
- Create a timestamped runtime evidence directory.
- Start Maven in the background.
- Capture PID, exact command, timestamps, and exit state.
- Write application output to `application.log`.
- Disable ANSI output where practical.
- Poll `/actuator/health` on port 8090.
- Leave the application running for curl validation.
- Stop only the tracked process and child process.

## Evidence

```text
target/evidence/<timestamp>-local-runtime/
├── 001-preflight.txt
├── 002-runtime-command.txt
├── 003-process-metadata.txt
├── 004-application.log
├── 005-health-headers.txt
├── 006-health-response.json
├── 007-health-status.txt
├── 008-stop-result.txt
└── 009-final-status.txt
```

## Security

- Do not use `env` or `printenv` in evidence.
- Do not log secret values.
- Do not archive `.local` private keys.
- Extract only sanitized failure sections for review.

## Acceptance criteria

- Application starts from the WSL-native repository.
- Redis TLS and Sentinel initialization succeed.
- Actuator health returns HTTP 200.
- File log is complete and readable.
- Stop command terminates Maven and Java cleanly.
- Windows curl can reach the WSL-hosted application through mirrored networking.
