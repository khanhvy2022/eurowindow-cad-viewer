# Rollback & Checkpoint Strategy

**Project:** Eurowindow CAD Viewer  
**Branch:** `feature/cad-viewer-p0`  

---

## 1. Checkpoint Policy
Before any phase is started:
1. Ensure `git status` is clean.
2. Tag the passing state: `git tag -a checkpoint-<phase>-before -m "Pre-phase checkpoint"`
3. Maintain isolated phase branches or small, discrete atomic commits.

## 2. Rollback Triggers
A rollback is immediately triggered if:
- Unit, contract, or instrumentation tests fail.
- License compliance is breached (e.g. accidental GPL contamination in release build).
- Source drawing files undergo unauthorized file modification.
- Critical memory leak or WebView renderer crash cannot be resolved within phase boundary.

## 3. Rollback Procedure
```bash
# 1. Stop current implementation
git status

# 2. Collect failure logs to evidence directory
# e.g., cp logs/build.log evidence/rollback-logs/

# 3. Discard uncommitted changes or revert the failing commit
git reset --hard HEAD~1
# OR revert specific phase commit
git revert <commit-hash> --no-edit

# 4. Verify baseline tests
npm test
# OR ./gradlew test
```
