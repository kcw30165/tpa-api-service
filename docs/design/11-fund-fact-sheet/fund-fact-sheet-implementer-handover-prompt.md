# Implementer Handover Prompt

Paste the text below as the first message of the implementation conversation, then attach the committed design pack (or an evidence export of `docs/design/11-fund-fact-sheet/`).

---

You are implementing the Fund Fact Sheet feature (BRD 6.8) in the Java BFF `api-service`.

**Repository:** `~/projects/orso/ngtpa/api-service` (WSL)
**Branch:** `feature/impl-fund-fact-sheet`, created from `origin/dev` at `c12d2cc4f23f87f5987252c6c3b55c2439355863`
**Design pack:** `docs/design/11-fund-fact-sheet/` (committed). It is the source of truth. Start with `0-fund-fact-sheet-design-pack-index.md` and `fund-fact-sheet-implementation-plan.md`.

**How we work**

- Investigate before designing or coding. Never infer behaviour from partial grep snippets; ask for a read-only investigation script instead.
- If the pack is wrong or incomplete, stop and propose a pack correction before writing code.
- You deliver Bash scripts. I run them and upload the evidence `.zip.txt`.
- Scripts are named `fund-fact-sheet-<NNN>-<purpose>.sh`. Numbers 001-017 are used (Stage 0 and its correction commit); the next script is **018**. Every new script, including fixes and reruns, gets a new, never-reused number.
- For every script, show the full filename in bold, then the file, then a text block with exactly: `run_notify "Script <filename>" bash /mnt/d/Projects/ORSO/llm-scripts/api-service/<filename>`, then the commit message (or "No commit").
- Each script writes numbered evidence files under `target/evidence/<script-name>-<timestamp>/`, packages them as `target/evidence/<script-name>.zip.txt`, and copies that file to `/mnt/d/Projects/ORSO/llm-scripts/api-service/`.
- Use `python`, not `python3`. Toolchain: Maven `/home/nd2887/tools/apache-maven`, JDK `/home/nd2887/tools/jdk-21.0.12.1+1`.
- Scripts validate with `mvn -DskipTests compile` and `mvn -DskipTests test-compile` only. No `mvn test` in scripts. `mvn spotless:apply` only in Stage 5; earlier scripts record that formatting was deferred.
- Before each stage transition I run the full test suite and a startup smoke test.
- Three consecutive failed scripts, or ten scripts without a commit: stop and escalate with root cause, repository state, risks and options.
- Do not modify Account Balance or Account Summary code or tests.
- Evidence dumps: prefix every line with `Lnnnn| `, write `<`, `>` and `${` as `[LT]`, `[GT]` and `[DLR]{`, and never emit `cat -n` output (the upload conversion drops lines that start with whitespace).
- Architecture guards scan source text: read implementation plan section 1.7 before writing any file under `adapter/out` or `adapter/in/web`.
- My shorthand: `r` = review the uploaded evidence, `c` = continue, `o` = move on.

**First task:** Stage 0 is complete. Generate the Stage 1 script (next free number): the shared `UrlValidator` and `UrlValidatorTest` in `application.service.presentation`, per the implementation plan.
