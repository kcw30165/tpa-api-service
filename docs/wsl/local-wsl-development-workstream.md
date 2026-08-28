# Local WSL Development Workstream

## 1. Purpose

Establish a smooth, persistent, Linux-native development workflow for the NGTPA Java BFF while preserving the correct operational boundaries:

- Windows retains VS Code, Docker Desktop, KeePass, Tanzu authentication, and Redis port forwarding.
- WSL owns Linux development commands, Git worktrees, Java 21, Maven 3.9.15, local runtime orchestration, curl validation, file-based logs, and evidence generation.
- Jenkins remains the sole authority for Harbor publishing, Helm deployment, Tanzu deployment, approvals, and artifact promotion.

## 2. Confirmed target decisions

1. Use build-once immutable artifact promotion for the future delivery model.
2. Refactor Jenkins later as a separate workstream.
3. Supply environment-specific behavior at runtime, without rebuilding the application image.
4. Replace routine Postman testing with scripted curl validation.
5. Keep file-based logs for direct Spring Boot and local-container execution.
6. Keep local Kubernetes usage limited to Redis development port forwarding.
7. Use Docker Desktop as the local Linux container engine.
8. Implement the WSL migration phase by phase, with evidence and rollback points.

## 3. Current-state baseline

- WSL distribution: Ubuntu 26.04 LTS.
- WSL networking: mirrored mode with DNS tunneling.
- Internal DNS and HTTPS access work.
- Windows-forwarded Redis Sentinel and Redis ports are reachable from WSL.
- WSL Git is installed.
- Java and Maven are not yet installed in WSL.
- Docker CLI is visible in WSL, but daemon access currently fails with permission denied on `/var/run/docker.sock`.
- Ubuntu 26.04 does not currently expose `openjdk-21-jdk` through the configured APT package sources.
- Existing Windows repositories remain unchanged during the baseline phase.

## 4. Phase map

| Phase | Name | Primary outcome | Detailed file |
|---|---|---|---|
| 01 | WSL baseline and toolchain | Native Java 21, Maven 3.9.15, Docker access, stable directories | `phase-01-wsl-baseline-and-toolchain.md` |
| 02 | Proof repository migration | One safe WSL-native BFF clone with rollback | `phase-02-proof-repository-migration.md` |
| 03 | Worktree-aware script workflow | Safe script inbox, target selection, execution, and archive | `phase-03-worktree-aware-script-workflow.md` |
| 04 | Direct Spring Boot runtime | Managed startup, health checking, shutdown, and file logs | `phase-04-direct-spring-runtime.md` |
| 05 | Curl functional validation | Repeatable health, login, OTP, Redis, APIM, and SMS evidence | `phase-05-curl-functional-validation.md` |
| 06 | Local Docker validation | Build and run the production Dockerfile locally | `phase-06-local-docker-validation.md` |
| 07 | Copilot evidence gates | Align Copilot A, Copilot B, and Nick completion gates | `phase-07-copilot-evidence-gates.md` |
| 08 | Jenkins quality correction | Real tests, deterministic checks, and GitLab status | `phase-08-jenkins-quality-correction.md` |
| 09 | Immutable artifact promotion | Build once and promote the same digest | `phase-09-immutable-artifact-promotion.md` |
| 10 | Runtime configuration and secrets | Remove rebuild drivers and improve secret handling | `phase-10-runtime-configuration-and-secrets.md` |

## 5. Cross-phase rules

### 5.1 Investigation and evidence

- Investigate before changing the environment or repository.
- Create timestamped evidence directories.
- Record exact commands, exit codes, concise summaries, relevant metadata, and final status.
- Do not duplicate full Maven or Docker output into evidence unless preserving failure diagnostics.
- Never archive credentials, kubeconfig, TLS private keys, populated secret files, tokens, OTP values, or sensitive headers.

### 5.2 Repository safety

- Do not delete or replace the current Windows repository during the proof phases.
- WSL Git owns WSL-native clones and worktrees.
- Windows Git owns the existing Windows checkout during transition.
- Every Copilot implementation script must verify repository, worktree, branch, and expected starting commit.

### 5.3 Toolchain

- Java must be version 21.
- Maven must be version 3.9.15.
- Docker must use the Docker Desktop Linux engine.
- Do not install a second Docker daemon inside WSL.
- Helm is not required for local development or local application deployment.
- Windows continues to own Tanzu login and Redis port forwarding.

### 5.4 Completion levels

- Copilot implementation gate: static investigation, source change, compile, test compilation, audits, diff, commit, and evidence.
- Local technical gate: application starts, file log is produced, and Actuator health succeeds.
- Local functional gate: scripted curl flow demonstrates the expected API behavior.
- Human observation gate: Nick confirms external effects such as SMS receipt.
- Jenkins gate: independent CI validation and immutable image construction.
- Tanzu gate: Jenkins deployment and post-deployment verification.

## 6. Workstream status

| Item | Status |
|---|---|
| Phase 01 baseline evidence | Created |
| Docker CLI integration | Partial, daemon permission failure remains |
| Java 21 selection | Blocked, APT package unavailable |
| Maven 3.9.15 installation | Not started |
| Repository migration | Not started |
| Curl suite | Not started |
| Local Docker runtime | Not started |
| Jenkins refactoring | Deferred |

## 7. Immediate next action

Continue Phase 01 only:

1. Diagnose Docker socket ownership and Docker Desktop integration.
2. Select a reproducible Java 21 installation method that does not depend on `openjdk-21-jdk` being present in Ubuntu 26.04 repositories.
3. Install Maven 3.9.15 after Java 21 passes.
4. Regenerate the Java evidence because the current `003-java-21.txt` contains invalid resolved paths and does not prove a Java installation.
