import time
import uuid
from typing import Optional, Union
from hound_pi.policy import StationaryPolicy
from hound_pi.protocol import (
    CommandAck,
    LocationCommand,
    MapState,
    VisionState,
    parse_line,
)
from hound_pi.radar_ld1125h import HLKLD1125HRadar
from hound_pi.sensor_fusion import fuse_vision_and_radar
from hound_pi.spi_driver import SPIRoverController
from hound_pi.transport import LineTransport


class AgentEngine:
    def __init__(
        self,
        policy: Optional[StationaryPolicy] = None,
        spi_controller: Optional[SPIRoverController] = None,
        radar: Optional[HLKLD1125HRadar] = None,
    ) -> None:
        self.policy = policy or StationaryPolicy()
        self.spi_controller = spi_controller
        self.radar = radar
        self.latest_map_state: Optional[MapState] = None

    def process_line(self, line: Union[str, bytes]) -> str:
        try:
            parsed = parse_line(line)
            if isinstance(parsed, VisionState):
                radar_det = self.radar.get_last_detection() if self.radar else None
                fused_obj = fuse_vision_and_radar(parsed, radar_det)
                if fused_obj is not None:
                    existing_objs = (
                        self.latest_map_state.objects if self.latest_map_state else []
                    )
                    # Filter out old fused target
                    filtered = [o for o in existing_objs if "Target" not in o.label]
                    filtered.append(fused_obj)
                    self.latest_map_state = MapState(
                        protocolVersion=1,
                        type="map_state",
                        timestampMs=int(time.time() * 1000),
                        roverX=0.0,
                        roverY=0.0,
                        roverHeading=0.0,
                        objects=filtered,
                    )

                motion_intent = self.policy.evaluate(parsed)
                if self.spi_controller is not None:
                    self.spi_controller.send_motion(
                        intent=motion_intent.intent.value,
                        duration_ms=motion_intent.durationMs,
                    )
                return motion_intent.model_dump_json(by_alias=True)

            elif isinstance(parsed, LocationCommand):
                if self.spi_controller is not None:
                    self.spi_controller.send_location(
                        target_x=parsed.targetX,
                        target_y=parsed.targetY,
                        speed=parsed.speed,
                    )
                ack = CommandAck(
                    protocolVersion=1,
                    type="command_ack",
                    commandId=parsed.id,
                    accepted=True,
                    reason="LOCATION_COMMAND_ACCEPTED",
                )
                return ack.model_dump_json(by_alias=True)
            elif isinstance(parsed, MapState):
                self.latest_map_state = parsed
                ack = CommandAck(
                    protocolVersion=1,
                    type="command_ack",
                    commandId=str(uuid.uuid4()),
                    accepted=True,
                    reason="MAP_STATE_UPDATED",
                )
                return ack.model_dump_json(by_alias=True)
            else:
                ack = CommandAck(
                    protocolVersion=1,
                    type="command_ack",
                    commandId=str(uuid.uuid4()),
                    accepted=False,
                    reason="INVALID_JSON",
                )
                return ack.model_dump_json(by_alias=True)
        except Exception:
            ack = CommandAck(
                protocolVersion=1,
                type="command_ack",
                commandId=str(uuid.uuid4()),
                accepted=False,
                reason="INVALID_JSON",
            )
            return ack.model_dump_json(by_alias=True)

    def run(self, transport: LineTransport) -> None:
        for line in transport.read_lines():
            response = self.process_line(line)
            transport.write_line(response)

