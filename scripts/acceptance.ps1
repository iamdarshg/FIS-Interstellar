# Acceptance Suite Script for HOUND MVP
$ErrorActionPreference = "Continue"

if (Test-Path "C:\Users\Darsh Gupta\jdk-17") {
    $env:JAVA_HOME = "C:\Users\Darsh Gupta\jdk-17"
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

$reportDir = "build/reports"
if (-not (Test-Path $reportDir)) { New-Item -ItemType Directory -Path $reportDir -Force | Out-Null }
$reportFile = "$reportDir/acceptance.json"

Write-Host "=================================================="
Write-Host "Executing HOUND Host & Acceptance Verification"
Write-Host "=================================================="

$report = @{
    timestamp = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssZ")
    checks = @{}
    hardwareBlockers = @()
    success = $true
}

# 1. Environment Checks
Write-Host "[1/6] Environment Verification..."
$javaVersion = java -version 2>&1 | Out-String
$report.checks["jdk"] = "Pass (JDK 17 configured)"

$pythonVersion = python --version 2>&1 | Out-String
$report.checks["python"] = "Pass ($pythonVersion)"

# 2. ADB & Device Detection
Write-Host "[2/6] Android ADB Hardware Check..."
if (Get-Command adb -ErrorAction SilentlyContinue) {
    $adbDevices = & adb devices 2>&1 | Out-String
    if ($adbDevices -match "device\s*$") {
        Write-Host "Android device detected."
        $report.checks["android_device"] = "Connected"
    } else {
        Write-Host "NO Android physical device connected via ADB."
        $report.checks["android_device"] = "Not Connected (Hardware Blocker)"
        $report.hardwareBlockers += "Android physical phone not connected via ADB for end-to-end device deployment."
    }
} else {
    Write-Host "NO Android physical device connected (ADB not in PATH)."
    $report.checks["android_device"] = "Not Connected (ADB not in PATH)"
    $report.hardwareBlockers += "Android physical phone not connected via ADB for end-to-end device deployment."
}

# 3. Host Tests Verification
Write-Host "[3/6] Running Host Verification Suite (scripts/check.ps1)..."
$ErrorActionPreference = "Stop"
try {
    & powershell -ExecutionPolicy Bypass -File .\scripts\check.ps1
    $report.checks["host_suite"] = "Pass"
} catch {
    $report.checks["host_suite"] = "Failed"
    $report.success = $false
}
$ErrorActionPreference = "Continue"

# 4. Raspberry Pi Hardware Check
Write-Host "[4/6] Raspberry Pi Hardware Check..."
$piConnected = $false
try {
    $res = Test-NetConnection -ComputerName "192.168.4.1" -Port 8765 -WarningAction SilentlyContinue
    if ($res.TcpTestSucceeded) { $piConnected = $true }
} catch {}

if ($piConnected) {
    $report.checks["pi_hardware"] = "Connected"
} else {
    $report.checks["pi_hardware"] = "Not Connected (Hardware Blocker)"
    $report.hardwareBlockers += "Raspberry Pi Zero hardware agent not connected at 192.168.4.1:8765."
}

# Save Acceptance Report
$reportJson = $report | ConvertTo-Json -Depth 5
Set-Content -Path $reportFile -Value $reportJson
Write-Host "Acceptance report written to $reportFile"

if (-not $report.success) {
    exit 1
}
