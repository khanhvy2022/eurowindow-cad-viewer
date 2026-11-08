# GSTARSOFT CAD SDK — CAPABILITY MATRIX

**Project:** Eurowindow CAD Viewer (`com.eurowindow.cadviewer`)  
**Evaluation Scope:** Viewer-Only Offline Android Integration  
**Discovery Date:** 2026-11-06  
**Documentation Source:** `https://dev.gstarcad.com/docs/` & `https://dwgfastview.gstarcad.net/cloud/cad-sdk/`

---

## 1. Classification Definitions
- **VERIFIED:** Documented in official Gstarsoft developer portal with documented API/sample code.
- **UNVERIFIED:** Claimed in general marketing or implied, but requires technical verification with actual vendor SDK package.
- **UNSUPPORTED:** Explicitly confirmed not supported by vendor architecture.
- **BLOCKED:** Dependent on proprietary SDK binary distribution or developer license currently unavailable in local workspace.

---

## 2. Capability Matrix

| ID | Feature / Capability | Classification | Technical Evidence & Discovery Notes |
| :--- | :--- | :--- | :--- |
| **CAP-01** | **Native DWG Viewing (AutoCAD R14 to 2018+)** | **VERIFIED (Docs) / BLOCKED (Binary)** | Official docs confirm DWG FastView engine natively parses and renders binary DWG formats without cloud conversion. Binary `.so` pending delivery. |
| **CAP-02** | **Native DXF Viewing (ASCII & Binary)** | **VERIFIED (Docs) / BLOCKED (Binary)** | Official docs confirm full DXF support. Binary pending delivery. |
| **CAP-03** | **Android OEM SDK Architecture (AAR/Libs/Assets)** | **VERIFIED** | Official docs describe SDK package structure: `assets/` (fonts, configurations), `libs/` (native `.so` for ARM/x86), `res/` (resources), and `src/`. |
| **CAP-04** | **Application Inheritance (`CadApplication`)** | **VERIFIED** | Host application `AndroidManifest.xml` must declare application subclassing `com.gstarSdk.CadApplication` or initialize SDK lifecycle in `Application.onCreate()`. |
| **CAP-05** | **File Opening via Intent (`dwgPath`)** | **VERIFIED** | Official docs specify launching `com.gstar.android.MainActivity` via `Intent.ACTION_VIEW` passing extra `intent.putExtra("dwgPath", filePath)`. |
| **CAP-06** | **SAF `content://` URI Support** | **UNVERIFIED** | Vendor docs specify local absolute file paths (`dwgPath`). Direct streaming from SAF `content://` URIs is unverified and likely requires caching stream to a local app-cache temp file. |
| **CAP-07** | **Offline Operation (No Internet Connection)** | **VERIFIED (Docs) / BLOCKED (License)** | SDK is engineered for offline viewing. However, commercial license verification behavior (one-time activation vs offline dongle/key) requires vendor confirmation. |
| **CAP-08** | **ARM64-v8a Architecture Support** | **VERIFIED** | Official SDK distributes 64-bit native binaries (`lib/arm64-v8a/*.so`). |
| **CAP-09** | **Viewport Navigation (Pan & Pinch-to-Zoom)** | **VERIFIED** | Built-in gestures handled directly by Gstarsoft native rendering viewport. |
| **CAP-10** | **Fit-to-Screen API** | **VERIFIED** | Default initial viewport centers and fits extents to screen. |
| **CAP-11** | **Layer Management (Show / Hide / Lock)** | **UNVERIFIED** | Exposed in full DWG FastView UI; API availability in bare embeddable OEM SDK view requires SDK header inspection. |
| **CAP-12** | **Layout Switching (Model Space vs Paper Space)** | **UNVERIFIED** | Standard in DWG FastView; OEM SDK Intent parameter or programmatic API to select layout tab needs verification. |
| **CAP-13** | **Offline Font Loading (SHX / TTF)** | **VERIFIED** | SDK includes `assets/` directory for bundled SHX and TrueType CAD fonts. |
| **CAP-14** | **Measurement Tools (Distance, Area)** | **UNVERIFIED** | Tooling exists in FastView UI; embedding without editing controls requires UI layout inspection. |
| **CAP-15** | **CAD Editing / Modification** | **UNSUPPORTED (By Scope)** | Excluded by Eurowindow CAD Viewer product charter (Strictly Viewer Only). |
| **CAP-16** | **Cloud Synchronization / Remote Storage** | **UNSUPPORTED (By Scope)** | Excluded by charter (Strictly Local Offline Viewing). |
| **CAP-17** | **Commercial Redistribution & Licensing** | **BLOCKED** | Requires developer account registration at `dev.gstarcad.com`, package name binding (`com.eurowindow.cadviewer`), and agreement execution. |

---

## 3. Summary Assessment

The Gstarsoft OEM SDK architecture is technically well-aligned with the Eurowindow CAD Viewer offline viewer requirements. The integration model (`CadApplication` + Intent/Activity or embedded CadView) avoids fragile web canvas emulations. 

However, because the SDK binaries and license files are proprietary and not publicly downloadable via public Maven/Gradle repositories, active implementation cannot proceed without receiving the official package from Gstarsoft.
