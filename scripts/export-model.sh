#!/usr/bin/env bash
set -e

if [ -z "$1" ]; then
  echo "Usage: ./scripts/export-model.sh <images-dir>"
  exit 2
fi

python tools/model/export_model.py --images-dir "$1"
