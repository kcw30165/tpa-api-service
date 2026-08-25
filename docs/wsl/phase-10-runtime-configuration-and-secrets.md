# Phase 10: Runtime Configuration and Secret Cleanup

## Objective

Ensure environment-specific behavior is supplied at runtime and remove security or configuration practices that force rebuilds or expose credentials.

## Activities

1. Inventory Dockerfile `ARG`, `ENV`, copied files, Maven profiles, and generated resources.
2. Classify every configuration value as build-time, runtime non-secret, or runtime secret.
3. Move environment-specific non-secret values to Helm values and ConfigMaps.
4. Move credentials and protection keys to managed Kubernetes Secrets or an approved secret manager.
5. Remove literal credentials from deployment values.
6. Remove `latest` as a deployment authority.
7. Review Maven TLS bypass flags in the Docker build.
8. Review `--insecure-skip-tls-verify` in Tanzu login.
9. Review request and response body logging for sensitive-data exposure.
10. Add automated checks that fail when secret patterns appear in source, evidence, or rendered non-secret configuration.

## Acceptance criteria

- Environment changes require rollout, not image rebuild.
- No literal operational credentials remain in Git-controlled Helm values.
- Secret-bearing local files are protected and excluded from evidence.
- Build and deployment TLS exceptions are documented, approved, or removed.
- The promoted image remains identical across environments.
