# Comprehensive License Audit & Legal Compliance Gate

**Project:** Eurowindow CAD Viewer  
**Milestone:** P0 — Offline CAD Viewer + Measurement  
**Auditor:** Antigravity (Technical Lead / Orchestrator)  
**Date:** 2026-11-05  
**Gate Decision:** CONDITIONAL PASS (Option C Recommended)

---

## 1. Full Dependency License Classification

| Package | Version | License | Redistribution Risk for Eurowindow Closed-Source App |
|---|---|---|---|
| `@mlightcad/cad-simple-viewer` | `1.7.8` | **MIT** | Zero. Permissive commercial use allowed. |
| `@mlightcad/data-model` | `1.15.22` | **MIT** | Zero. Permissive commercial use allowed. |
| `@mlightcad/three-renderer` | `1.7.8` | **MIT** | Zero. Permissive commercial use allowed. |
| `@mlightcad/mtext-parser` | `1.5.7` | **MIT** | Zero. Permissive commercial use allowed. |
| `@mlightcad/mtext-renderer` | `0.13.7` | **MIT** | Zero. Permissive commercial use allowed. |
| `@mlightcad/common` | `1.15.22` | **MIT** | Zero. Permissive commercial use allowed. |
| `@mlightcad/geometry-engine` | `3.15.22` | **MIT** | Zero. Permissive commercial use allowed. |
| `@mlightcad/graphic-interface` | `3.15.22` | **MIT** | Zero. Permissive commercial use allowed. |
| `@mlightcad/emf-converter` | `1.15.22` | **Apache-2.0** | Zero. Permissive commercial use allowed. |
| `three` | `0.186.1` | **MIT** | Zero. Permissive commercial use allowed. |
| `lodash-es` | `4.17.21` | **MIT** | Zero. Permissive commercial use allowed. |
| `mitt` | `3.0.1` | **MIT** | Zero. Permissive commercial use allowed. |
| `rbush` | `4.0.1` | **MIT** | Zero. Permissive commercial use allowed. |
| `@velipso/polybool` | `1.1.1` | **0BSD** | Zero. Public domain / permissive. |
| `@mlightcad/libredwg-converter` | `3.15.22` | **GPL-3.0** | **CRITICAL RISK** if bundled statically in APK. |
| `@mlightcad/libredwg-web` | `0.8.4` | **GPL-3.0** | **CRITICAL RISK** (GNU LibreDWG WebAssembly port). |
| `@mlightcad/dwg-converter` | Private | **Commercial** | Requires $3,000 USD perpetual license purchase. |

---

## 2. In-Depth Analysis of LibreDWG Integration

GNU LibreDWG is licensed under **GNU General Public License v3.0 (GPL-3.0)**.

### Questions Addressed:
1. **Is LibreDWG included in the default dependency tree of `cad-simple-viewer`?**  
   **NO.** Inspection of `@mlightcad/cad-simple-viewer/package.json` confirms that it only lists `three`, `lodash-es`, `@mlightcad/data-model`, `@mlightcad/mtext-parser`, `@mlightcad/mtext-renderer`, and `@mlightcad/three-renderer` as peer dependencies. It does **not** depend on or ship `@mlightcad/libredwg-converter`.
2. **Is it dynamically or statically integrated?**  
   It is integrated dynamically via `AcDbDatabaseConverterManager.instance.register(AcDbFileType.DWG, converter)`. The application host must explicitly instantiate and register `AcDbLibreDwgConverter`.
3. **Are worker and WASM assets redistributed in the APK?**  
   If `@mlightcad/libredwg-converter` is included in the web runtime build, `libredwg-parser-worker.js` and `libredwg-web.wasm` (11.5 MB) must be physically packaged in the Android `assets/` folder.
4. **Do GPL obligations apply to the intended distribution?**  
   **YES.** Under GPL-3.0 Section 6 (Conveying Non-Source Forms), distributing a combined mobile binary (APK) containing GPL-3.0 compiled WebAssembly code obligates the distributor to provide the Corresponding Source of the complete combined work to any recipient, under terms compatible with GPL-3.0.
5. **Can the project be distributed as a closed-source Eurowindow application under Option B?**  
   **NO.** Packaging GPL-3.0 LibreDWG directly inside a proprietary Eurowindow APK creates significant legal exposure and conflicts with closed-source commercial IP policies.

---

## 3. Evaluation of Strategic Options

### Option A: MIT Stack + Commercially Licensed DWG Parser
- **Mechanism:** Purchase `@mlightcad/dwg-converter` ($3,000 perpetual license).
- **Pros:** Full closed-source compliance, smaller memory footprint, faster parsing of heavy DWG files.
- **Cons:** Commercial cost ($3,000 USD) + manual corporate procurement timeline.
- **Feasibility for P0:** Cannot be done without corporate budget sign-off.

### Option B: MIT Stack + GPL-3.0 LibreDWG Bundled
- **Mechanism:** Ship `libredwg-web.wasm` inside the APK.
- **Pros:** Immediate DWG loading out-of-the-box at zero license fee.
- **Cons:** Violates closed-source requirement; triggers GPL copyleft risk across APK distribution.
- **Decision:** **REJECTED** for public / commercial distribution.

### Option C: Pure MIT Base (DXF Out-of-the-box) + Pluggable DWG Ingestion Gate (RECOMMENDED)
- **Mechanism:**
  1. Base APK release includes **100% MIT & Apache-2.0** code: full DXF viewing, 3D/2D rendering, layer management, and measurement tools.
  2. DWG ingestion is architected via an abstract, pluggable `CadEngineConverter` interface.
  3. For internal engineering evaluation and testing in P0 development environments, the user can toggle the LibreDWG engine via a local debug config flag (`ENABLE_GPL_EVALUATION=true`), or integrate the commercial parser when corporate procurement is complete.
  4. The standalone production release APK remains 100% clean of GPL code.
- **Decision:** **APPROVED / RECOMMENDED FOR P0.**
