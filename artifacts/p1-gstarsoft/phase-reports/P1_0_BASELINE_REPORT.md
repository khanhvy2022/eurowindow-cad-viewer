# EUROWINDOW CAD VIEWER — PHASE P1.0 BASELINE REPORT

**Project:** Eurowindow CAD Viewer (`com.eurowindow.cadviewer`)  
**Workspace:** `F:\EW DWG View`  
**Verified Baseline Commit:** `43347d56a95c3c273184f94128444222c16f558e` (`43347d5`)  
**Verified Baseline Tag:** `checkpoint-p0.1`  
**Target Branch:** `feature/cad-viewer-p1-gstarsoft`  
**Verification Date:** 2026-11-06T08:08:00+07:00  
**Phase P1.0 Status:** AUDIT COMPLETE — IMPLEMENTATION GATE: **BLOCKED** (Awaiting vendor SDK package delivery)

---

## 1. Executive Summary

Phase P1.0 establishes the verified baseline freeze and performs the official technical and licensing discovery for the Gstarsoft (DWG FastView) Android CAD SDK integration.

- **Baseline Freeze:** Fully verified against commit `43347d5` and tag `checkpoint-p0.1`.
- **Protected Core:** 32 protected core files mapped, SHA-256 hashed, and validated with zero modifications.
- **Corpus Integrity:** 14/14 DXF test fixtures verified byte-identical.
- **SDK Procurement Status:** The Gstarsoft Android CAD SDK is a commercial proprietary product requiring formal vendor registration (`dev.gstarcad.com` / `support_app@gstarcad.com`). Because physical SDK distribution binaries (`.aar`, `.so`, license credentials) have not yet been placed into the repository or licensed to `com.eurowindow.cadviewer`, Phase P1.1 (Isolated Android Spike) is **BLOCKED** in strict compliance with Rule 1 and Phase P1.0 Gate criteria.

---

## 2. Git & Working Tree Verification

| Item | Value | Status |
| :--- | :--- | :--- |
| Baseline Commit | `43347d56a95c3c273184f94128444222c16f558e` (`43347d5`) | **VERIFIED** |
| Tag | `checkpoint-p0.1` pointing to `43347d5` | **VERIFIED** |
| Starting Branch | `feature/cad-viewer-p0` | **VERIFIED** |
| New Branch | `feature/cad-viewer-p1-gstarsoft` (branched from `43347d5`) | **ACTIVE** |
| Tracked File Diffs | 0 tracked modifications | **CLEAN** |
| Test Corpus | 14/14 DXF files matching baseline SHA-256 | **PASS (100%)** |

All baseline audit logs and checksums are persisted in:
- `artifacts/p1-gstarsoft/baseline/baseline.json`
- `artifacts/p1-gstarsoft/baseline/baseline-git-status.txt`
- `artifacts/p1-gstarsoft/baseline/baseline-sha256.txt`
- `artifacts/p1-gstarsoft/protected-core/protected-core-manifest.json`

---

## 3. Protected Core Manifest Summary

The 32 Protected Core files comprise the core architecture, P0 baseline, build configurations, and test fixtures:
1. **Core Specifications & Documentation:** `README.md`, `PHASE_P0_1_SPEC.md`, `artifacts/P0_1_EVIDENCE_PACK.md`, `artifacts/P0_1_GAP_ANALYSIS.md`, `artifacts/P0_1_VERIFICATION_REPORT.md`
2. **Android App Wrapper:** `android/app/build.gradle`, `android/build.gradle`, `android/settings.gradle`, `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/java/com/eurowindow/cadviewer/MainActivity.kt`, `android/app/src/main/res/values/strings.xml`, `android/app/src/main/res/values/styles.xml`
3. **Web CAD Runtime Assets:** `cad-engine/web-runtime/index.html`, `cad-engine/web-runtime/mlightcad-runtime.js`, `cad-engine/web-runtime/engine-bridge.js`, `cad-engine/web-runtime/package.json`
4. **Verification Scripts:** `scripts/verify_p01_evidence.mjs`, `scripts/verify_file_integrity.mjs`, `cad-engine/web-runtime/verify-corpus.mjs`
5. **Fixture Corpus (14/14 DXF files):** `fixtures/sample_door_2d.dxf`, `fixtures/sample_facade_2d.dxf`, `fixtures/sample_window_2d.dxf`, and 11 additional test fixtures in `fixtures/corpus/`.

No Protected Core file has been altered or deleted.

---

## 4. Carried-Forward P0.1 Findings

As required by Section 3 of the P1 specification, existing P0.1 findings are preserved transparently:
- **DXF Rendering:** Chrome CDP test passed (sheet_0: 11,858 entities, 283.18 ms).
- **Android WebView:** Not yet independently verified on hardware.
- **OPPO Physical Device:** Status was PARTIAL / BLOCKED in P0.1 due to ADB device connectivity during unattended test execution.
- **Known P0.1 Defect:** `getLayers()` iterator API defect remains documented (layer count reported as 0 in web runtime).
- **Font Fallback:** Runtime still contains jsDelivr CDN font fallback when fonts are missing locally.
- **DWG Modal Notice:** In P0.1, opening a DWG displays the educational dialog directing users to DXF format. P1 replaces this with the official Gstarsoft DWG viewing engine once the SDK is provided.

---

## 5. Next Steps

Execution of Phase P1.1 (Isolated Android Spike) will begin immediately upon receipt of the official Gstarsoft Android OEM SDK package and trial license key from Gstarsoft.
