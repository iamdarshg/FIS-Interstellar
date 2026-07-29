# USB Dashboard Forwarding Script for HOUND
# Reverses port 8080 so the local host browser can connect to phone Ktor dashboard via USB

$ErrorActionPreference = "Stop"

Write-Host "Setting up ADB port reverse for HOUND Dashboard (tcp:8080)..."
if (Get-Command adb -ErrorAction SilentlyContinue) {
    adb reverse tcp:8080 tcp:8080
    Write-Host "SUCCESS: Dashboard available at http://localhost:8080"
} else {
    Write-Host "WARNING: adb command not found in PATH."
    exit 1
}
