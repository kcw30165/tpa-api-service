# Username Recovery XSRF Cookie Removal Handover

## Repair
- Restored valid Start and Cancel reactive controller pipelines after the damaged broad-regex edit.
- UsernameRecoveryCookieWriter now supports auth_flow_sid only.
- Start sets exactly auth_flow_sid while returning csrfToken in the response body.
- Request and Cancel continue to accept X-XSRF-TOKEN and validate against the server-side session hash.
- Cancel clears exactly auth_flow_sid.
- Updated affected unit and public-contract integration tests.

## Preserved cumulative state
- Redis UsernameRecoveryWorkflowStatePort bean registration remains in UsernameRecoveryFeatureConfig.
- Username Recovery public system errors remain scoped to err.system.unavailable.
- Shared workflow error keys remain unchanged.

## Evidence
target/evidence/20260812-224300-username-recovery-xsrf-removal-repair
