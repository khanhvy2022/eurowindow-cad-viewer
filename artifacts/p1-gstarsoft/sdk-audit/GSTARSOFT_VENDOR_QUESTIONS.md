# TECHNICAL & LICENSING INQUIRY FOR GSTARSOFT (DWG FASTVIEW) SDK TEAM

**To:** Gstarsoft Mobile SDK Team (`support_app@gstarcad.com` / `dev.gstarcad.com`)  
**From:** Eurowindow Engineering Team  
**Subject:** Technical Integration Inquiry & Trial SDK Request for Android OEM CAD Viewer  
**Target Package Name:** `com.eurowindow.cadviewer`  
**Target Platform:** Android 8.0+ (API 26+) — Architecture: ARM64 (`arm64-v8a`)  
**Date:** 2026-11-06

---

### 1. SDK Package & Evaluation Access
1. **Trial SDK Distribution:** Could you provide the latest Android OEM SDK evaluation package (`.aar` or `.jar` + native `.so` libraries + `assets` font bundle) along with an evaluation license key bound to `com.eurowindow.cadviewer`?
2. **Sample Project:** Do you provide a clean, standalone Android Studio sample project demonstrating minimal viewer-only integration?

---

### 2. Offline Licensing & Security Requirements
3. **100% Air-Gapped / Offline Usage:** Eurowindow engineers and factory technicians frequently operate in construction sites and secure plant facilities without internet connectivity. Does the SDK require recurring online license heartbeats or telemetry, or can it operate completely offline once initialized?
4. **License Key Binding:** How is the developer license verified at runtime (e.g., license file in `assets/`, API call `CadLicense.init("...")`, or static signature check)?

---

### 3. Architecture & Integration Depth
5. **View Component vs Activity:** The documentation describes launching `com.gstar.android.MainActivity` with `dwgPath`. 
   - Is there also an embeddable `View` or `Fragment` (e.g., `CadSurfaceView`, `CadView`) available if we wish to embed the CAD viewport directly within our own custom Android Jetpack Compose or Kotlin layout?
   - If using `MainActivity`, can the standard DWG FastView toolbars, cloud buttons, and export menus be cleanly suppressed via Intent extras or configuration to present a pure read-only viewer interface?
6. **SAF (`content://` URI) Compatibility:** Modern Android versions (Android 11–14+) enforce Scoped Storage. When a user selects a file via `Intent.ACTION_OPEN_DOCUMENT`, we receive a `content://` URI rather than a `/storage/emulated/0/...` path. 
   - Does the SDK's file opener accept `ParcelFileDescriptor` or input streams, or does Gstarsoft recommend copying the stream into app-private cache (`context.cacheDir`) before invoking the viewer?

---

### 4. CAD Engine Capabilities & File Formats
7. **DWG Version Support:** Does the current Android SDK support AutoCAD DWG binary format versions up to R2018/R2024?
8. **Font Rendering & Missing Font Handling:** How does the SDK handle missing Asian/Vietnamese BigFonts or custom `.shx` fonts? Can custom font directories be appended at runtime?
9. **Layer Control APIs:** Does the SDK expose programmatic APIs to toggle layer visibility and inspect layer names?

---

### 5. Licensing Model & Commercial Terms
10. **Commercial Licensing Structure:** What are the commercial licensing options for internal enterprise redistribution (e.g., perpetual per-application license vs annual subscription vs per-device license)?
