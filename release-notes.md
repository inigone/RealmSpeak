## Changes (IL-commit-063)

- **AppUpdater restart heap raised** — restart after in-app update now uses `-Xms1g -Xmx4g` so large games don't run out of memory
- **Update loop fixed** — `version.txt` is written after install so the update dialog no longer re-offers the same release on every launch
- **Release build automated** — `ant -buildfile build/release-build.xml` now pushes the branch, derives the version and release number, builds the zip, and publishes to GitHub; no arguments needed
