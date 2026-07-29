# HOUND Vision Test Fixtures

This directory contains test fixture manifests and synthetic scenario generators for HOUND vision regression testing.

## Synthetic Fixtures
Run `python generate_synthetic.py` to deterministically create test frame metadata and synthetic scenario definitions using seed `20260729`.

## Private Fixtures
To run regression tests against local real-world recordings, set the environment variable `HOUND_PRIVATE_FIXTURES` to the path containing your local recordings manifest.
Results will be output to `build/reports/private_fixtures_report.json` and will never be committed.
