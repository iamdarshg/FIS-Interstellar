#!/usr/bin/env bash
set -e

echo "=== Running Android Unit Tests and Lint ==="
cd android
./gradlew testDebugUnitTest lintDebug
cd ..

echo "=== Running Ruff Lint ==="
python -m ruff check pi tools

echo "=== Running MyPy Type Check ==="
python -m mypy pi/hound_pi

echo "=== Running Pytest with Coverage ==="
python -m pytest pi/tests tools/tests --cov=pi/hound_pi --cov-branch --cov-fail-under=95
