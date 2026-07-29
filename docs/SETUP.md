# HOUND System Setup Guide

## Prerequisites
- **JDK:** Java 17 (JDK 17.0.x)
- **Android SDK:** Compile & Target SDK 35, Min SDK 26
- **Python:** Python 3.11+
- **Gradle:** 8.11.1 wrapper bundled in repository

## Environment Setup
1. Set `JAVA_HOME` to your JDK 17 installation directory.
2. Set `ANDROID_HOME` to your local Android SDK location.
3. Install Python dependencies:
   ```bash
   python -m pip install -e .
   ```

## Model Exporter
Generate the quantized INT8 target embedding model:
```bash
python tools/model/export_model.py
```
This produces `android/app/src/main/assets/hound_embedding_v1.tflite`.
