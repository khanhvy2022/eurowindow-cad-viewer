# MLightCAD Repository & Ecosystem Audit

**Audit Date:** 2026-11-05  
**Auditor:** Antigravity (Technical Lead / Orchestrator)  
**Source Repository:** `https://github.com/mlightcad/cad-viewer`  
**Git Commit Inspected:** `8ce90b5eb89dd3a58042c9d16c1bf37afff319b2` (release: release v1.8.0-preview.0)  
**Latest Stable Tag:** `v1.8.0-preview.0` (npm release tag: `1.7.8` latest, `1.7.8-preview.6`)  
**License:** MIT License (mlightcad)

---

## 1. Monorepo Structure & Packages

The upstream repository is a pnpm/Nx monorepo containing 25 modular packages:

| Package | Current Version | npm Status | Role / Responsibility |
|---|---|---|---|
| `@mlightcad/cad-simple-viewer` | `1.8.0-preview.0` (npm: `1.7.8`) | Published | Core standalone lightweight viewer shell & MDI manager (`AcApDocManager`). |
| `@mlightcad/three-renderer` | `1.8.0-preview.0` (npm: `1.7.8`) | Published | Three.js WebGL rendering engine (`AcTrRenderer`, `AcTrView2d`). |
| `@mlightcad/cad-editor` | `1.8.0-preview.0` | Monorepo | Full CAD workspace & editing tools. |
| `@mlightcad/cad-simple-ui-plugin` | `1.8.0-preview.0` | Monorepo | Built-in UI widgets, toolbars, and layout tabs. |
| `@mlightcad/cad-demo-gallery` | `1.8.0-preview.0` | Monorepo | Drawing gallery & preset viewer test harness. |
| `@mlightcad/cad-diff-viewer` | `1.8.0-preview.0` | Monorepo | Visual CAD revision diffing & comparison engine. |
| `@mlightcad/cad-pdf-exporter` | `1.8.0-preview.0` | Monorepo | Client-side vector PDF generator. |
| `@mlightcad/cad-svg-plugin` | `1.8.0-preview.0` | Monorepo | Vector SVG export plugin. |

External Core Packages (from `@mlightcad` ecosystem):
- `@mlightcad/data-model` (v`1.15.22`): Headless in-memory AutoCAD data model (`AcDbDatabase`, `AcDbEntity`, `AcDbLayerTableRecord`, etc.). License: **MIT**.
- `@mlightcad/mtext-renderer` (v`0.13.7`): Specialized Web Worker text shaping & layout engine. License: **MIT**.
- `@mlightcad/mtext-parser` (v`1.5.7`): Fast DXF MTEXT format parser. License: **MIT**.
- `@mlightcad/geometry-engine` (v`3.15.22`): 2D/3D computational geometry math engine. License: **MIT**.
- `@mlightcad/graphic-interface` (v`3.15.22`): Rendering abstraction layer. License: **MIT**.
- `@mlightcad/emf-converter` (v`1.15.22`): Windows Enhanced Metafile converter. License: **Apache-2.0**.
- `@mlightcad/libredwg-converter` (v`3.15.22`): LibreDWG WebAssembly wrapper. License: **GPL-3.0**.
- `@mlightcad/dwg-converter`: Private proprietary commercial DWG parser ($3,000 perpetual).

---

## 2. Core Functional Modules Inspection

### 2.1 DWG & DXF Parser Registration
- **DXF Parsing:** Embedded directly in `@mlightcad/data-model` as a native TypeScript parser. No WebAssembly or external binaries required. Fast, safe, pure MIT.
- **DWG Parsing:** Pluggable converter architecture via `AcDbDatabaseConverterManager`:
  ```typescript
  import { AcDbDatabaseConverterManager, AcDbFileType } from '@mlightcad/data-model';
  // Converter is registered on demand; cad-simple-viewer does NOT bundle a DWG converter by default.
  AcDbDatabaseConverterManager.instance.register(AcDbFileType.DWG, converter);
  ```

### 2.2 Measurement Modules
Inspected source directory: `packages/cad-simple-viewer/src/command/measure/`
- `AcApMeasureDistanceCmd.ts`: Point-to-point Euclidean & orthogonal distance.
- `AcApMeasureAngleCmd.ts`: 3-point vertex angle or 2-line angle measurement.
- `AcApMeasureAreaCmd.ts`: Multi-point closed polygon area & perimeter calculation.
- `AcApMeasureArcCmd.ts`: Radius, chord length, and arc angle.
- `AcApMeasurementStore.ts` & `AcApMeasurementSidecar.ts`: **Strict separation from CAD drawing data**. Measurements are recorded as independent sidecar JSON metadata. Original DWG/DXF files remain 100% read-only and unmodified.

### 2.3 Mobile Touch & Gesture Implementation
Inspected source directory: `packages/cad-simple-viewer/src/editor/input/ui/` & `src/view/AcTrView2d.ts`:
- Built-in `acedAttachMobileBoxGesture`: Native pointer-event gesture recognition for single-touch pan and multi-touch pinch-to-zoom.
- `AcEdTouchPointSession`: Magnifier / loupe callout for precise finger point snapping on high-DPI screens.
- `acedGuardCanvasTouchCallout`: Touch cancel & ghost-click suppression for mobile webviews.

### 2.4 Worker & WASM Assets
- `mtext-renderer-worker.js`: Asynchronous font glyph shaping in background worker (MIT).
- `libredwg-parser-worker.js` + `libredwg-web.wasm`: GPL-3.0 worker bundle for DWG decoding. Must be loaded via same-origin URL (e.g. `https://cad.eurowindow.local/workers/`).

---

## 3. Upstream Code Health & Maintenance
- Actively maintained by upstream author (`mlightcad`), commits logged through November 2026.
- Comprehensive unit test suite with Jest, Vite, and Playwright E2E fixtures.
- Clean separation between data model, rendering canvas, and input sessions.
