# HOUND Troubleshooting Guide

## Common Issues & Solutions

### 1. Gradle JVM Incompatibility Error
- **Symptom:** Build fails with `25.0.3` or `JVM is incompatible`.
- **Cause:** Shell environment has Java 25 set as `JAVA_HOME`.
- **Fix:** Set `JAVA_HOME` to JDK 17 (`C:\Users\Darsh Gupta\jdk-17`).

### 2. Dashboard Connection Refused
- **Symptom:** `http://localhost:8080` does not open in host browser.
- **Fix:** Run ADB port reverse command:
  ```powershell
  adb reverse tcp:8080 tcp:8080
  ```

### 3. Raspberry Pi Connection Timeout
- **Symptom:** Transport health indicates `DISCONNECTED` or `ERROR`.
- **Fix:** Verify USB tethering / Wi-Fi network and ensure `hound-pi.service` is running on port 8765.
