# Viewy — development rules

## Scope and engineering
- Read `docs/PRODUCT_SPEC.md` for product intent and `docs/ARCHITECTURE.md` for current technical contracts; inspect relevant code before changing it.
- Work on one explicitly approved, bounded change at a time. Prefer correctness, simplicity, maintainability and portability over speculative abstractions.
- Keep measurement mathematics independent of Android/UI; preserve offline operation and honest uncertainty/accuracy claims. Add focused tests for logic that matters.
- Do not perform unrelated refactors, change local configuration, or add dependencies without a concrete need.

## Resource and device boundaries
- Protect the developer's RAM and normal desktop use. Prefer slower, bounded, sequential builds over concurrent or persistent workers.
- Run only checks relevant to the change. For Gradle, prefer a **single invocation** with `./gradlew --no-daemon --max-workers=1 --no-watch-fs <relevant-tasks>`; avoid parallel Gradle invocations, continuous builds, unnecessary `clean` and repeated full builds. A temporary single-use JVM may still be created by Gradle.
- Do not start ADB, query ADB (`adb devices` also starts a server), invoke `scripts/deploy.sh`, use device discovery, install/launch apps, start emulators, or perform physical-device testing unless explicitly authorized for that task.
- Leave hardware, calibration and usability validation to the developer; provide concise manual steps when needed. Do not claim tests were run if they were not.
- Avoid background services or long-lived watcher/processes. Reap processes started by your task when feasible; do not kill unrelated Java/ADB processes or alter global developer settings.

## Documentation and Git
- Document **what the product does and why**, not the internal task sequence. Never refer to work as "Phase A/B/C", milestones, prompts or task numbers in user-facing text, permanent documentation, or commit messages.
- Prefer updating an authoritative document over creating a new Markdown file. Keep product intent in `docs/PRODUCT_SPEC.md`, essential implemented contracts in `docs/ARCHITECTURE.md`, reproducible verification and unverified limitations in `docs/VALIDATION.md`, and setup/use in `README.md`. If `docs/VALIDATION.md` has not yet been established, use the current verification document until consolidation.
- Do not duplicate source code, test cases, transient logs, build counts, or historical narratives in permanent docs. Change docs only where the actual task changes the documented facts.
- Use conventional commits describing the actual code change (for example `fix(camera): handle unavailable physical lens`), never the planning stage. Do not commit or push without explicit permission.
- End with a compact report: changes, justification, relevant checks and outcomes, remaining limitations and any manual validation needed.
