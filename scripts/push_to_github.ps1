# One-Click Push to GitHub Script for Eurowindow CAD Viewer

param(
    [string]$RepoUrl = "https://github.com/khanhvy2022/eurowindow-cad-viewer.git",
    [string]$Branch = "feature/cad-viewer-p0"
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Eurowindow CAD Viewer - Push to GitHub & Release APK" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

Set-Location "F:\EW DWG View"

$currentRemote = git remote get-url origin 2>$null

if (-not $currentRemote) {
    Write-Host "[Git] Adding remote 'origin' -> $RepoUrl" -ForegroundColor Yellow
    git remote add origin $RepoUrl
} else {
    Write-Host "[Git] Remote 'origin' already points to: $currentRemote" -ForegroundColor Green
    if ($currentRemote -ne $RepoUrl) {
        Write-Host "[Git] Updating remote to $RepoUrl..." -ForegroundColor Yellow
        git remote set-url origin $RepoUrl
    }
}

Write-Host "`n[Git] Current commits ready to push:" -ForegroundColor Cyan
git log --oneline -n 3

Write-Host "`n[Git] Pushing branch '$Branch' and tags to GitHub..." -ForegroundColor Yellow
git push -u origin $Branch --tags

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n==========================================================" -ForegroundColor Green
    Write-Host "Pushed to GitHub successfully!" -ForegroundColor Green
    Write-Host "Repository: $RepoUrl" -ForegroundColor Green
    Write-Host "GitHub Actions will automatically build & attach the APK under Releases/Artifacts." -ForegroundColor Cyan
    Write-Host "==========================================================" -ForegroundColor Green
} else {
    Write-Host "`nPush failed. Please ensure:" -ForegroundColor Red
    Write-Host "  1. You have created an empty repository on GitHub: $RepoUrl" -ForegroundColor Yellow
    Write-Host "  2. You have write permissions to the repository." -ForegroundColor Yellow
    Write-Host "  3. To push to a different repository name, run:" -ForegroundColor White
    Write-Host "     powershell -File scripts/push_to_github.ps1 -RepoUrl 'https://github.com/<YOUR_USER>/<YOUR_REPO>.git'" -ForegroundColor White
}
