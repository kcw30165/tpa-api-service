# Phase 01: WSL Baseline and Toolchain

## Objective

Prepare a persistent Ubuntu WSL development environment with native Linux Git, Java 21, Maven 3.9.15, Docker Desktop access, internal network access, and safe development directories. Do not move a repository in this phase.

## Scope

### Included

- Capture WSL baseline evidence.
- Confirm internal DNS, HTTPS, and Redis-forward reachability.
- Enable and verify Docker Desktop integration.
- Resolve Docker socket permissions without installing another daemon.
- Install a reproducible Linux Java 21 distribution.
- Install Maven 3.9.15 under the WSL home directory.
- Create non-secret toolchain configuration.
- Create project, Copilot inbox, archive, state, tools, and configuration directories.

### Excluded

- Repository migration.
- Application source modification.
- Helm installation.
- Tanzu application deployment.
- Jenkins modification.
- Secret migration.

## Current evidence

### Passed

- `001-wsl-baseline.txt` exists.
- Ubuntu 26.04 LTS is confirmed.
- Git 2.53.0 is available.
- Internal service DNS resolution works.
- Internal HTTPS returns HTTP 401, proving network and application reachability.
- Windows-forwarded Redis ports 26379 and 6379 are reachable.

### Partial or failed

- Docker CLI 29.7.2 is installed in WSL, but access to `/var/run/docker.sock` is denied.
- Docker server details were not captured.
- `openjdk-21-jdk` is not available from current APT sources.
- `003-java-21.txt` is invalid because it reports `java=/home/nd2887`, `javac=/home/nd2887`, and `JAVA_HOME=/` without an installed Java executable.

## Stage 01A: Diagnose Docker access

Do not install `docker.io` or start a second Docker daemon.

Collect:

```bash
id
getent group docker || true
ls -l /var/run/docker.sock || true
stat -c 'owner=%U group=%G mode=%a path=%n' /var/run/docker.sock || true
command -v docker
docker context ls
```

Decision logic:

1. If the socket group is `docker` and the user is not in that group, add the WSL user to the group, restart the WSL session, and test again.
2. If the socket has an unexpected owner or group, inspect Docker Desktop WSL integration before changing permissions.
3. Do not use `chmod 666 /var/run/docker.sock` as a permanent fix.
4. Do not use `sudo docker` as the normal development contract.

Required evidence:

```text
~/ngtpa-setup-evidence/002A-docker-permission-diagnosis.txt
~/ngtpa-setup-evidence/002B-docker-integration-result.txt
```

Pass criteria:

- `docker version` shows both client and server.
- Server OS is Linux.
- Normal user access works without `sudo`.
- Windows and WSL use the same Docker Desktop engine.

## Stage 01B: Select and install Java 21

APT does not currently provide `openjdk-21-jdk`, so do not install the Ubuntu default Java 25 as a substitute.

Evaluate these candidates in order:

1. Company-approved internal Java 21 archive or software repository.
2. A vendor Java 21 Linux x64 tar archive approved by the company.
3. SDK version manager only if installation and external-repository use are permitted.

Preferred installation layout:

```text
~/tools/jdk-21/
```

Required validation:

```bash
"$HOME/tools/jdk-21/bin/java" --version
"$HOME/tools/jdk-21/bin/javac" --version
```

Both must report major version 21 and Linux x64.

Required evidence:

```text
~/ngtpa-setup-evidence/003-java-21.txt
```

The evidence script must first verify `command -v java` and `command -v javac`. It must not call `readlink` on empty command results.

Pass criteria:

- Java and javac both report version 21.
- Java paths are inside WSL, not `/mnt/c` or `/mnt/d`.
- Java survives `wsl --shutdown` and restart.

## Stage 01C: Install Maven 3.9.15

Install under:

```text
~/tools/apache-maven-3.9.15/
~/tools/apache-maven -> apache-maven-3.9.15
```

Validate the archive checksum before extraction.

Pass criteria:

```bash
"$HOME/tools/apache-maven/bin/mvn" --version
```

must report:

- Apache Maven 3.9.15
- Java 21
- Unix family

Required evidence:

```text
~/ngtpa-setup-evidence/004-maven-3.9.15.txt
```

## Stage 01D: Configure non-secret development tools

Create:

```text
~/.config/ngtpa/dev-tools.sh
```

It may define only non-secret values:

```bash
export JAVA_HOME="$HOME/tools/jdk-21"
export MAVEN_HOME="$HOME/tools/apache-maven"
export M2_HOME="$MAVEN_HOME"
export PATH="$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH"
export MAVEN_OPTS="-Dfile.encoding=UTF-8"
```

Source it from `.bashrc` only if the file exists.

Do not write credentials, API keys, passwords, OTP values, Kubernetes credentials, or TLS private keys into this file.

## Stage 01E: Prepare directory structure

Create:

```text
~/projects/orso/ngtpa/
~/projects/orso/ngtpa/api-service-worktrees/
~/copilot/inbox/
~/copilot/archive/
~/copilot/state/
~/.config/ngtpa/
~/tools/
```

## Phase acceptance criteria

- Baseline evidence retained.
- Docker works as the normal WSL user.
- Java 21 is native to WSL.
- Maven 3.9.15 runs with Java 21.
- Internal network and forwarded Redis ports remain reachable.
- No source repository was moved.
- No second Docker daemon was installed.
- No secret was written to evidence.

## Rollback

- Disable Docker Desktop integration for Ubuntu if it causes instability.
- Remove only the newly created WSL tool directories and profile include.
- Existing Windows tools, repositories, and Redis-forward process remain unchanged.
