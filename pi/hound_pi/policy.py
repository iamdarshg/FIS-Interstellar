import time
import uuid
from hound_pi.protocol import MotionIntent, MotionKind, VisionState


class StationaryPolicy:
    """Policy for stationary build mode. Always returns MotionKind.STOP duration 0."""

    def evaluate(self, vision_state: VisionState) -> MotionIntent:
        return MotionIntent(
            protocolVersion=1,
            type="motion_intent",
            id=str(uuid.uuid4()),
            sentAtMs=int(time.time() * 1000),
            intent=MotionKind.STOP,
            durationMs=0,
            reason="stationary_mode"
        )
