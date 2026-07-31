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


class AutonomousNavigationPolicy:
    """Simultaneous Goal-Seeking & Hazard-Avoidance Navigation Policy."""

    def __init__(self, mission_mode: str = "OBJECT_FINDING") -> None:
        self.mission_mode = mission_mode

    def evaluate(self, vision_state: VisionState) -> MotionIntent:
        if vision_state.mode.name != "TRACKED" or vision_state.targetBox is None:
            return MotionIntent(
                protocolVersion=1,
                type="motion_intent",
                id=str(uuid.uuid4()),
                sentAtMs=int(time.time() * 1000),
                intent=MotionKind.STOP,
                durationMs=0,
                reason="no_tracked_target"
            )

        box = vision_state.targetBox
        x_center = (box.xMin + box.xMax) / 2.0
        reason_str = vision_state.reason or ""
        is_hazard = "HAZARD" in reason_str or self.mission_mode == "OBJECT_AVOIDANCE"

        if is_hazard:
            # HAZARD AVOIDANCE: Steer AWAY from hazard obstacle
            if x_center < 0.45:
                intent = MotionKind.ROTATE_RIGHT
                reason = "hazard_avoid_steer_right"
            elif x_center > 0.55:
                intent = MotionKind.ROTATE_LEFT
                reason = "hazard_avoid_steer_left"
            else:
                intent = MotionKind.ROTATE_RIGHT
                reason = "hazard_avoid_sharp_evade"
            duration = 200
        else:
            # GOAL SEEKING: Steer TOWARDS goal object
            if x_center < 0.40:
                intent = MotionKind.ROTATE_LEFT
                reason = "goal_seeking_turn_left"
                duration = 150
            elif x_center > 0.60:
                intent = MotionKind.ROTATE_RIGHT
                reason = "goal_seeking_turn_right"
                duration = 150
            else:
                intent = MotionKind.DRIVE_FORWARD
                reason = "goal_seeking_drive_forward"
                duration = 300

        return MotionIntent(
            protocolVersion=1,
            type="motion_intent",
            id=str(uuid.uuid4()),
            sentAtMs=int(time.time() * 1000),
            intent=intent,
            durationMs=duration,
            reason=reason
        )
