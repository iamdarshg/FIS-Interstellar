# USB Pi Tunnel Verification Script for HOUND
# Documents USB tethering / USB IP link and tests the Pi raw TCP control port without changing protocol message format.

param(
    [string]$PiHost = "192.168.4.1",
    [int]$Port = 8766
)

$ErrorActionPreference = "Stop"

Write-Host "=================================================="
Write-Host "HOUND USB Pi Tunnel & Tethering Test"
Write-Host "Target: $PiHost:$Port"
Write-Host "Protocol: Newline-delimited JSON (Version 1)"
Write-Host "=================================================="

Write-Host "Testing connectivity to Raspberry Pi..."
try {
    $res = Test-NetConnection -ComputerName $PiHost -Port $Port -WarningAction SilentlyContinue
    if ($res.TcpTestSucceeded) {
        Write-Host "SUCCESS: Connected to Pi at ${PiHost}:${Port}"
    } else {
        Write-Host "WARNING: Could not connect to ${PiHost}:${Port}. Verify USB tethering / Wi-Fi IP and Pi service status."
    }
} catch {
    Write-Host "ERROR: Connection test failed: $_"
}
