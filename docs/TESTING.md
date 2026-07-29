# HOUND Testing Matrix & Definition of Done

## Definition of Done Verification Matrix

| Requirement | Verification Command | Expected Outcome |
| --- | --- | --- |
| Android Unit & Lint | `android\gradlew.bat -p android testDebugUnitTest lintDebug` | 0 failures, 0 lint errors |
| Python Pytest & Coverage | `python -m pytest pi/tests tools/tests --cov=pi/hound_pi --cov-fail-under=95` | >= 95% branch coverage |
| Property Tests | JUnit/Kotest & Hypothesis suites | 1,000 generated cases pass |
| Vision Regression Corpus | `android\gradlew.bat -p android :vision:test --tests '*RecordedScenarioTest'` | All scenarios pass |
| Acceptance Suite | `powershell -ExecutionPolicy Bypass -File .\scripts\acceptance.ps1` | `acceptance.json` generated |
| Memory & Soak | `powershell -ExecutionPolicy Bypass -File .\scripts\soak.ps1` | `soak.json` generated |

## Hardware Gates
- **Android Physical Phone:** Required for live camera capture and hardware performance soak.
- **Raspberry Pi Zero:** Required for physical motor rotation and HIL network latency test.
- When hardware is missing, automated tests execute with fake adapters and output clear hardware blocker entries in test reports.
