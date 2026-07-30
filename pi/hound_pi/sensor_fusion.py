import math
import time
import uuid
from typing import Any, Optional
from hound_pi.protocol import Object2D, VisionState


def fuse_vision_and_radar(
    vision_state: VisionState,
    radar_detection: Optional[dict[str, Any]] = None,
    camera_fov_deg: float = 60.0,
    default_distance: float = 1.5,
) -> Optional[Object2D]:
    """Fuses camera vision bounding box with HLK-LD1125H mmWave radar distance sensor data.

    Camera provides horizontal bearing angle theta from target box center.
    HLK-LD1125H radar provides exact millimeter-wave distance reading.
    Combined output produces precise 2D position (x, y) relative to rover body.
    """
    if vision_state.targetBox is None:
        return None

    box = vision_state.targetBox
    x_center = (box.xMin + box.xMax) / 2.0
    angle_deg = (x_center - 0.5) * camera_fov_deg
    angle_rad = math.radians(angle_deg)

    # Use radar exact distance if detected
    distance = default_distance
    fused_label = "Vision Target"

    if radar_detection is not None and radar_detection.get("targetDetected", False):
        radar_dist = float(radar_detection.get("distanceMeters", 0.0))
        if radar_dist > 0.1:
            distance = radar_dist
            fused_label = "Fused Vision+Radar Target"

    # Convert polar (distance, angle) to 2D Cartesian (x, y) relative to rover
    target_x = distance * math.sin(angle_rad)
    target_y = distance * math.cos(angle_rad)

    return Object2D(
        id=str(uuid.uuid4()),
        label=fused_label,
        x=round(target_x, 3),
        y=round(target_y, 3),
        confidence=round(vision_state.confidence, 2),
        distance=round(distance, 3),
        angle=round(angle_deg, 1),
        lastSeenMs=int(time.time() * 1000),
    )
