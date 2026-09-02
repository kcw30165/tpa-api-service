# API Service Handover

## Phase 3D3B update, 20260902T074113Z

Phase 3D3B was applied from exact baseline `6cd621bb89c177406d025440155294c667999e9d`. The stale generic single-profile APIM subsection was removed. The four active credential profiles and their shared base URL, token URI, scope, certificate path, profile-specific credentials, and API keys remain intact. Redis and Jenkins deployment configuration are unchanged.

## Phase 3D5B update, 20260902T083601Z

Phase 3D5B was applied from exact baseline `103e3ec2ac152b9c0d67b0d0e188c6081fe54885`. The shared non-empty `REDIS_SENTINEL_PASSWORD` fallback was replaced with an empty external placeholder. Optional password application remains guarded by the existing Redis configuration. All other Redis topology and runtime settings are unchanged.
