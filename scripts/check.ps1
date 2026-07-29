$ErrorActionPreference = "Stop"

if (Test-Path "C:\Users\Darsh Gupta\jdk-17") {
    $env:JAVA_HOME = "C:\Users\Darsh Gupta\jdk-17"
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}
if (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
}

$exitCode = 0

Write-Host "=== Running Android Unit Tests and Lint ==="
Push-Location android
try {
    .\gradlew.bat testDebugUnitTest lintDebug
    if ($LASTEXITCODE -ne 0 -and $exitCode -eq 0) { $exitCode = $LASTEXITCODE }
} finally {
    Pop-Location
}

if ($exitCode -ne 0) { exit $exitCode }

Write-Host "=== Running Ruff Lint ==="
python -m ruff check pi tools
if ($LASTEXITCODE -ne 0 -and $exitCode -eq 0) { $exitCode = $LASTEXITCODE }
if ($exitCode -ne 0) { exit $exitCode }

Write-Host "=== Running MyPy Type Check ==="
python -m mypy pi/hound_pi
if ($LASTEXITCODE -ne 0 -and $exitCode -eq 0) { $exitCode = $LASTEXITCODE }
if ($exitCode -ne 0) { exit $exitCode }

Write-Host "=== Running Pytest with Coverage ==="
python -m pytest pi/tests tools/tests --cov=pi/hound_pi --cov-branch --cov-fail-under=95
if ($LASTEXITCODE -ne 0 -and $exitCode -eq 0) { $exitCode = $LASTEXITCODE }

exit $exitCode
