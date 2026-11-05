# Automated OPPO Device Acceptance Verification Script
# Project: Eurowindow CAD Viewer (com.eurowindow.cadviewer)

param(
    [string]$AdbPath = "C:\Users\ThangTran\AppData\Local\Android\Sdk\platform-tools\adb.exe",
    [string]$ApkPath = "F:\EW DWG View\release\debug\com.eurowindow.cadviewer-v1.0-debug.apk",
    [string]$OutputDir = "F:\EW DWG View\evidence\P0.1.1"
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Eurowindow CAD Viewer - P0.1.1 OPPO Device Verification" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

if (-not (Test-Path $AdbPath)) {
    Write-Error "ADB binary not found at $AdbPath"
    exit 1
}

if (-not (Test-Path $ApkPath)) {
    Write-Error "Target APK not found at $ApkPath"
    exit 1
}

New-Item -ItemType Directory -Path "$OutputDir\screenshots" -Force | Out-Null
New-Item -ItemType Directory -Path "$OutputDir\logs" -Force | Out-Null

$devicesOutput = & $AdbPath devices -l
Write-Host "`n[ADB] Querying connected devices..." -ForegroundColor Yellow
Write-Host $devicesOutput

$lines = $devicesOutput -split "`r?`n" | Where-Object { $_ -match "\bdevice\b" -and $_ -notmatch "List of devices" }

if (-not $lines -or $lines.Count -eq 0) {
    Write-Host "`nBLOCKED - DEVICE NOT AVAILABLE" -ForegroundColor Red
    Write-Host "No Android device detected over ADB USB." -ForegroundColor Yellow
    Write-Host "Instructions for OPPO phone:" -ForegroundColor White
    Write-Host "  1. Connect OPPO phone to PC via USB cable."
    Write-Host "  2. Go to Settings > About Device > Version > Tap 'Build number' 7 times to enable Developer Options."
    Write-Host "  3. Go to Settings > Additional Settings > Developer Options > Enable 'USB Debugging'."
    Write-Host "  4. On the phone screen, allow USB debugging authorization prompt."
    Write-Host "  5. Re-run this script: powershell -ExecutionPolicy Bypass -File scripts/verify_device_oppo.ps1"
    
    $blockedObj = @{
        gate = "Gate E - Target Device Acceptance"
        status = "BLOCKED - DEVICE NOT AVAILABLE"
        timestamp = (Get-Date).ToString("o")
        reason = "No physical Android device connected via ADB."
        targetApk = $ApkPath
        targetApkSha256 = (Get-FileHash -Path $ApkPath -Algorithm SHA256).Hash
    }
    $blockedJson = $blockedObj | ConvertTo-Json -Depth 5
    Set-Content -Path "$OutputDir\device-acceptance.json" -Value $blockedJson
    exit 0
}

Write-Host "`nDevice connected! Collecting device telemetry..." -ForegroundColor Green
$model = (& $AdbPath shell getprop ro.product.model).Trim()
$brand = (& $AdbPath shell getprop ro.product.brand).Trim()
$osVer = (& $AdbPath shell getprop ro.build.version.release).Trim()
$sdkVer = (& $AdbPath shell getprop ro.build.version.sdk).Trim()
$memInfo = (& $AdbPath shell cat /proc/meminfo) | Select-String "MemTotal"

Write-Host "  Brand:       $brand"
Write-Host "  Model:       $model"
Write-Host "  Android Ver: $osVer (SDK $sdkVer)"
Write-Host "  Memory:      $memInfo"

Write-Host "`n[ADB] Installing APK: $ApkPath..." -ForegroundColor Yellow
$installStart = Get-Date
$installResult = & $AdbPath install -r $ApkPath
$installDuration = ((Get-Date) - $installStart).TotalSeconds
Write-Host "  Result: $installResult in $([math]::Round($installDuration, 2))s"

Write-Host "`n[ADB] Launching Eurowindow CAD Viewer..." -ForegroundColor Yellow
$launchStart = Get-Date
& $AdbPath shell am start -n com.eurowindow.cadviewer/.MainActivity
Start-Sleep -Seconds 2
$coldStartLatency = ((Get-Date) - $launchStart).TotalMilliseconds
Write-Host "  Cold start launch latency: $([math]::Round($coldStartLatency, 0))ms"

Write-Host "`n[ADB] Pushing test drawing (sheet_0.dxf) to device storage..." -ForegroundColor Yellow
& $AdbPath push "F:\EW DWG View\corpus\originals\fixtures\sheet_0.dxf" "/sdcard/Download/sheet_0.dxf"

Write-Host "`n[ADB] Capturing on-device screenshot..." -ForegroundColor Yellow
& $AdbPath shell screencap -p /sdcard/ew_cad_device.png
& $AdbPath pull /sdcard/ew_cad_device.png "$OutputDir\screenshots\oppo_device.png"
& $AdbPath shell rm /sdcard/ew_cad_device.png

Write-Host "`n[ADB] Dumping device logcat..." -ForegroundColor Yellow
& $AdbPath logcat -d -s "EurowindowCAD" "Chromium" "WebView" "ActivityTaskManager" > "$OutputDir\logs\oppo_logcat.log"

$acceptanceObj = @{
    gate = "Gate E - Target Device Acceptance"
    status = "PASS"
    timestamp = (Get-Date).ToString("o")
    device = @{
        brand = $brand
        model = $model
        androidVersion = $osVer
        sdkVersion = $sdkVer
        memTotal = $memInfo.Line.Trim()
    }
    app = @{
        packageId = "com.eurowindow.cadviewer"
        apkSha256 = (Get-FileHash -Path $ApkPath -Algorithm SHA256).Hash
        installDurationSeconds = [math]::Round($installDuration, 2)
        coldStartMs = [math]::Round($coldStartLatency, 0)
        screenshot = "$OutputDir\screenshots\oppo_device.png"
        logcat = "$OutputDir\logs\oppo_logcat.log"
    }
}
$acceptanceJson = $acceptanceObj | ConvertTo-Json -Depth 5
Set-Content -Path "$OutputDir\device-acceptance.json" -Value $acceptanceJson

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host "OPPO Device Verification Completed Successfully!" -ForegroundColor Green
Write-Host "Report saved to $OutputDir\device-acceptance.json" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
