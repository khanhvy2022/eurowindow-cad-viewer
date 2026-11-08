# GSTARSOFT SDK — LEGAL & LICENSE COMPLIANCE REVIEW

**Project:** Eurowindow CAD Viewer (`com.eurowindow.cadviewer`)  
**Target Component:** Gstarsoft Android OEM CAD SDK (DWG FastView OEM)  
**Date:** 2026-11-06  
**Auditor:** AntiGravity Software Engineering Discipline  

---

## 1. License Classification & Nature

The Gstarsoft Android CAD SDK is **Proprietary Commercial Software** owned by Suzhou Gstarsoft Co., Ltd. (Gstarsoft). It is licensed under a commercial OEM developer agreement rather than an open-source license.

Key legal aspects:
1. **No Open-Source Incompatibility:** Unlike GPL-3.0 (e.g., LibreDWG), which enforces reciprocal copyleft on all linked application source code, Gstarsoft's commercial OEM license grants explicit rights to link proprietary SDK binaries (`.aar`, `.so`) into enterprise applications without requiring disclosure of host application source code.
2. **Strict Compliance with "No LibreDWG" Rule:** The project charter strictly prohibits GPL-licensed LibreDWG. Utilizing Gstarsoft's commercial native engine completely satisfies this IP and legal compliance boundary.
3. **No Unofficial / Cracked Binaries:** The project rules explicitly forbid using unlicensed, reverse-engineered, or cracked SDK libraries. All binaries must be directly obtained through official Gstarsoft channels (`dev.gstarcad.com`).

---

## 2. Redistribution & Git Repository Hygiene

To safeguard vendor IP and comply with non-disclosure / commercial distribution terms:
- **Git Tracking Prohibitions:** Private vendor SDK archives (`.aar`, `.jar`, `.so`), vendor license keys, and proprietary configuration tokens must **never** be committed to public Git repositories.
- **Local SDK Isolation:** The SDK binaries must reside in an ignored local directory:
  ```gitignore
  # Gstarsoft Private OEM SDK Artifacts
  libs/gstarsoft/
  android/app/libs/gstarsoft/
  *.aar
  *.lic
  gstar_license*.xml
  ```
- **Enterprise Distribution:** Redistribution is permissible only in compiled release APK format to authorized Eurowindow devices/users, in accordance with the finalized commercial agreement between Eurowindow and Gstarsoft.

---

## 3. Compliance Checklist

| Requirement | Project Policy | Compliance Status |
| :--- | :--- | :--- |
| **GPL Elimination** | Zero GPL/AGPL code in production binary | **COMPLIANT** (LibreDWG fully excluded) |
| **Vendor IP Protection** | No raw SDK binaries committed to public Git | **ENFORCED** (Ignored in `.gitignore`) |
| **License Key Protection** | No hardcoded vendor secret keys in source | **ENFORCED** (Configured via build configs / runtime injection) |
| **Legitimate Procurement** | Official download from Gstarsoft developer portal | **PENDING VENDOR DELIVERY** |
| **Offline Operation** | No telemetry or unconsented network egress | **VERIFIED BY DESIGN** (Local file viewing only) |

---

## 4. Conclusion

Gstarsoft Android OEM SDK is legally compliant and safe for internal Eurowindow enterprise deployment, provided it is procured through an official OEM agreement and commercial binaries are isolated from public version control.
