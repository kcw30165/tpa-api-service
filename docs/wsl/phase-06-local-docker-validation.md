# Phase 06: Local Docker Validation

## Objective

Build the production Dockerfile locally through Docker Desktop, run the resulting Linux container, retain file-based logs, and execute the curl validation suite.

## Activities

1. Build from the same Dockerfile and context used by Jenkins.
2. Use a local-only image tag tied to Git commit and timestamp.
3. Record image ID and digest information available locally.
4. Run a uniquely named container.
5. Supply environment variables at container startup, not image build time.
6. Mount Redis TLS material read-only.
7. Connect the container to Windows-forwarded Redis through a tested host-gateway arrangement.
8. Resolve the Sentinel-returned Redis hostname without weakening TLS verification.
9. Publish application and management ports only to localhost.
10. Capture `docker logs` to a timestamped file.
11. Poll Actuator health.
12. Run curl functional validation.
13. Stop and remove only the tracked validation container.
14. Do not push to Harbor.

## Evidence

```text
target/evidence/<timestamp>-local-container/
├── 001-preflight.txt
├── 002-build-command.txt
├── 003-build-summary.txt
├── 004-image-inspect.json
├── 005-run-summary.txt
├── 006-container-inspect.json
├── 007-container.log
├── 008-health-response.json
├── 009-functional-validation-manifest.txt
├── 010-stop-and-cleanup.txt
└── 011-validation-status.txt
```

## Acceptance criteria

- Production Dockerfile builds locally.
- No environment-specific value is baked into the image.
- Container reaches Redis, APIM, configuration service, and required HTTP services.
- Actuator health succeeds.
- Curl validation succeeds for the selected flow.
- Container log is retained and sanitized.
- No image is pushed and no cluster deployment occurs.
