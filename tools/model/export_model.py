import argparse
import hashlib
import json
import os
import sys
from pathlib import Path

from PIL import Image
import numpy as np


def main() -> None:
    parser = argparse.ArgumentParser(description="Export reproducible INT8 MobileNetV3 embedding model")
    parser.add_argument("--images-dir", required=True, help="Path to directory containing exactly 100 representative images")
    parser.add_argument(
        "--output-tflite",
        default="android/app/src/main/assets/hound_embedding_v1.tflite",
        help="Target output path for tflite model",
    )
    parser.add_argument(
        "--output-metadata",
        default="tools/model/model-metadata.json",
        help="Target output path for model metadata JSON",
    )
    args = parser.parse_args()

    images_dir = Path(args.images_dir)
    if not images_dir.exists() or not images_dir.is_dir():
        print(f"ERROR: Images directory '{images_dir}' does not exist.", file=sys.stderr)
        sys.exit(2)

    image_files = sorted(
        [p for p in images_dir.iterdir() if p.suffix.lower() in (".jpg", ".jpeg", ".png")]
    )
    if len(image_files) != 100:
        print(
            f"ERROR: Expected exactly 100 image files in '{images_dir}', but found {len(image_files)}.",
            file=sys.stderr,
        )
        sys.exit(2)

    loaded_images = []
    for p in image_files:
        try:
            with Image.open(p) as img:
                img_rgb = img.convert("RGB").resize((128, 128))
                arr = np.array(img_rgb, dtype=np.uint8)
                if arr.shape != (128, 128, 3):
                    raise ValueError(f"Unexpected image shape {arr.shape}")
                loaded_images.append(arr)
        except Exception as e:
            print(f"ERROR: Corrupt or unreadable image file '{p}': {e}", file=sys.stderr)
            sys.exit(2)

    import tensorflow as tf

    import keras

    base_model = tf.keras.applications.MobileNetV3Small(
        input_shape=(128, 128, 3),
        include_top=False,
        weights="imagenet",
        pooling="avg",
        include_preprocessing=True,
    )
    base_model.trainable = False

    inputs = keras.Input(shape=(128, 128, 3), dtype="uint8")
    x = keras.ops.cast(inputs, "float32")
    outputs = base_model(x)
    model = keras.Model(inputs=inputs, outputs=outputs)

    def representative_dataset_gen():
        for arr in loaded_images:
            yield [np.expand_dims(arr, axis=0)]

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.representative_dataset = representative_dataset_gen
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    converter.inference_input_type = tf.uint8
    converter.inference_output_type = tf.int8

    try:
        tflite_model = converter.convert()
    except Exception as e:
        print(f"ERROR: TFLite conversion failed: {e}", file=sys.stderr)
        sys.exit(2)

    output_tflite_path = Path(args.output_tflite)
    output_tflite_path.parent.mkdir(parents=True, exist_ok=True)
    output_tflite_path.write_bytes(tflite_model)

    sha256_hash = hashlib.sha256(tflite_model).hexdigest()

    metadata = {
        "modelName": output_tflite_path.name,
        "inputShape": [1, 128, 128, 3],
        "inputDtype": "uint8",
        "outputShape": [1, 576],
        "outputDtype": "int8",
        "sha256": sha256_hash,
    }

    output_metadata_path = Path(args.output_metadata)
    output_metadata_path.parent.mkdir(parents=True, exist_ok=True)
    output_metadata_path.write_text(json.dumps(metadata, indent=2), encoding="utf-8")

    print(f"SUCCESS: Exported model to {output_tflite_path} (SHA-256: {sha256_hash})")


if __name__ == "__main__":
    main()
