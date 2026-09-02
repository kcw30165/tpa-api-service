# Phase 3D3B: Remove Stale Single-Profile APIM Configuration

## Implemented

- Removed the stale generic single-profile APIM subsection using `APIM_CLIENT_ID`, `APIM_CLIENT_SECRET`, and `APIM_BASE_URL`.
- Removed only the `APIM_TOKEN_URI` usage enclosed by that stale subsection.
- Preserved the active `apim.credential-profiles.profiles` configuration and all four profiles.
- Preserved shared `APIM_BASEURL`, `APIM_TOKEN_URI`, `APIM_CLIENT_SCOPE`, certificate path, profile-specific credentials, and profile-specific API keys.
- Added a focused resource-contract test preventing stale generic configuration from returning.

## Preserved

Redis Sentinel defaults, compatibility aliases, Jenkins values, APIM endpoints, Kubernetes Secret contracts, and Production Harbor Proxy Cache behavior were not changed.
