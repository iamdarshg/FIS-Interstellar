import json
import subprocess
import sys
from pathlib import Path
import pytest
from PIL import Image, ImageDraw
import numpy as np


def create_synthetic_images(target_dir: Path, count: int = 100) -> None:
    target_dir.mkdir(parents=True, exist_ok=True)
    np.random.seed(20260729)
    for i in range(count):
        img = Image.new("RGB", (128, 128), color=(i % 256, (i * 3) % 256, (i * 7) % 256))
        draw = ImageDraw.Draw(img)
        draw.rectangle([10 + (i % 20), 10, 50, 50], fill=((i * 13) % 256, 128, 200))
        img.save(target_dir / f"img_{i:03d}.png")


def test_export_model_success(tmp_path: Path) -> None:
    images_dir = tmp_path / "rep_images"
    create_synthetic_images(images_dir, 100)

    tflite_path = tmp_path / "hound_embedding_v1.tflite"
    metadata_path = tmp_path / "model-metadata.json"

    cmd = [
        sys.executable,
        "tools/model/export_model.py",
        "--images-dir",
        str(images_dir),
        "--output-tflite",
        str(tflite_path),
        "--output-metadata",
        str(metadata_path),
    ]

    res = subprocess.run(cmd, capture_output=True, text=True)
    assert res.returncode == 0, f"Exporter failed: {res.stderr}\n{res.stdout}"
    assert tflite_path.exists()
    assert metadata_path.exists()

    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    assert metadata["inputShape"] == [1, 128, 128, 3]
    assert metadata["inputDtype"] == "uint8"
    assert metadata["outputShape"] == [1, 576]
    assert metadata["outputDtype"] == "int8"
    assert len(metadata["sha256"]) == 64

    import tensorflow as tf

    try:
        interpreter = tf.lite.Interpreter(model_path=str(tflite_path))
        interpreter.allocate_tensors()
    except Exception:
        interpreter = tf.lite.Interpreter(
            model_path=str(tflite_path),
            experimental_op_resolver_type=tf.lite.experimental.OpResolverType.BUILTIN_WITHOUT_DEFAULT_DELEGATES,
        )
        interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    assert input_details[0]["shape"].tolist() == [1, 128, 128, 3]
    assert input_details[0]["dtype"] == np.uint8
    assert output_details[0]["shape"].tolist() == [1, 576]
    assert output_details[0]["dtype"] == np.int8

    test_img = np.ones((1, 128, 128, 3), dtype=np.uint8) * 128
    interpreter.set_tensor(input_details[0]["index"], test_img)
    interpreter.invoke()
    output1 = interpreter.get_tensor(output_details[0]["index"])

    interpreter.set_tensor(input_details[0]["index"], test_img)
    interpreter.invoke()
    output2 = interpreter.get_tensor(output_details[0]["index"])

    assert output1.shape == (1, 576)
    assert np.all(np.isfinite(output1))
    assert np.array_equal(output1, output2)


def test_export_model_negative_99_images(tmp_path: Path) -> None:
    images_dir = tmp_path / "images_99"
    create_synthetic_images(images_dir, 99)

    cmd = [
        sys.executable,
        "tools/model/export_model.py",
        "--images-dir",
        str(images_dir),
    ]
    res = subprocess.run(cmd, capture_output=True, text=True)
    assert res.returncode == 2
    assert "Expected exactly 100 image files" in res.stderr


def test_export_model_negative_corrupt_image(tmp_path: Path) -> None:
    images_dir = tmp_path / "images_corrupt"
    create_synthetic_images(images_dir, 99)
    corrupt_file = images_dir / "img_099.png"
    corrupt_file.write_bytes(b"NOT_AN_IMAGE_FILE_HEADER_CORRUPT")

    cmd = [
        sys.executable,
        "tools/model/export_model.py",
        "--images-dir",
        str(images_dir),
    ]
    res = subprocess.run(cmd, capture_output=True, text=True)
    assert res.returncode == 2
    assert "Corrupt or unreadable image file" in res.stderr
