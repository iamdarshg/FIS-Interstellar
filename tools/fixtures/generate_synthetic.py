"""Deterministic synthetic fixture generator for HOUND vision regression suite."""

import json
from pathlib import Path
import random

SEED = 20260729


def generate_scenarios() -> dict:
    random.seed(SEED)
    scenarios = {
        "learn": {
            "id": "learn",
            "seed": SEED,
            "width": 320,
            "height": 240,
            "frames": [
                {
                    "frameIndex": i,
                    "timestampMs": i * 100,
                    "similarity": 0.85 + random.uniform(-0.02, 0.02),
                    "box": {"xMin": 0.3, "yMin": 0.3, "xMax": 0.7, "yMax": 0.7},
                }
                for i in range(12)
            ],
        },
        "distractor": {
            "id": "distractor",
            "seed": SEED,
            "width": 320,
            "height": 240,
            "frames": [
                {
                    "frameIndex": i,
                    "timestampMs": i * 100,
                    "similarity": 0.45 + random.uniform(-0.1, 0.1),
                    "box": {"xMin": 0.1, "yMin": 0.1, "xMax": 0.4, "yMax": 0.4},
                }
                for i in range(10)
            ],
        },
        "occlude_reacquire": {
            "id": "occlude_reacquire",
            "seed": SEED,
            "width": 320,
            "height": 240,
            "frames": [
                {
                    "frameIndex": i,
                    "timestampMs": i * 100,
                    "similarity": 0.88 if i not in range(4, 9) else 0.20,
                    "box": {"xMin": 0.3, "yMin": 0.3, "xMax": 0.7, "yMax": 0.7},
                }
                for i in range(15)
            ],
        },
        "occlude_lost": {
            "id": "occlude_lost",
            "seed": SEED,
            "width": 320,
            "height": 240,
            "frames": [
                {
                    "frameIndex": i,
                    "timestampMs": i * 100,
                    "similarity": 0.88 if i < 3 else 0.10,
                    "box": {"xMin": 0.3, "yMin": 0.3, "xMax": 0.7, "yMax": 0.7},
                }
                for i in range(40)
            ],
        },
    }
    return scenarios


def main() -> None:
    output_dir = Path(__file__).parent / "generated"
    output_dir.mkdir(exist_ok=True)
    scenarios = generate_scenarios()
    for name, data in scenarios.items():
        with open(output_dir / f"{name}.json", "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)


if __name__ == "__main__":
    main()
