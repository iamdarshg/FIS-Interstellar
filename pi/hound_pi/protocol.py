from enum import Enum
import json
from typing import Any, Literal, Union
import uuid
from pydantic import BaseModel, ConfigDict, Field, ValidationError, field_validator


class MotionKind(str, Enum):
    STOP = "STOP"
    ROTATE_LEFT = "ROTATE_LEFT"
    ROTATE_RIGHT = "ROTATE_RIGHT"
    DRIVE_FORWARD = "DRIVE_FORWARD"


class VisionMode(str, Enum):
    IDLE = "IDLE"
    LEARNING = "LEARNING"
    SEARCHING = "SEARCHING"
    TRACKED = "TRACKED"
    OCCLUDED = "OCCLUDED"
    LOST = "LOST"


class BoundingBox(BaseModel):
    model_config = ConfigDict(frozen=True, extra="forbid")

    xMin: float = Field(..., ge=0.0, le=1.0)
    yMin: float = Field(..., ge=0.0, le=1.0)
    xMax: float = Field(..., ge=0.0, le=1.0)
    yMax: float = Field(..., ge=0.0, le=1.0)

    @field_validator("xMax")
    @classmethod
    def check_x_order(cls, v: float, info: Any) -> float:
        if "xMin" in info.data and v < info.data["xMin"]:
            raise ValueError("xMax must be >= xMin")
        return v

    @field_validator("yMax")
    @classmethod
    def check_y_order(cls, v: float, info: Any) -> float:
        if "yMin" in info.data and v < info.data["yMin"]:
            raise ValueError("yMax must be >= yMin")
        return v


class MotionIntent(BaseModel):
    model_config = ConfigDict(frozen=True, extra="forbid")

    protocolVersion: Literal[1] = 1
    type: Literal["motion_intent"] = "motion_intent"
    id: str
    sentAtMs: int = Field(..., ge=0)
    intent: MotionKind
    durationMs: int = Field(..., ge=0, le=500)
    reason: str

    @field_validator("id")
    @classmethod
    def check_uuid(cls, v: str) -> str:
        try:
            uuid.UUID(v)
        except ValueError:
            raise ValueError("id must be a valid UUID format")
        return v


class VisionState(BaseModel):
    model_config = ConfigDict(frozen=True, extra="forbid")

    protocolVersion: Literal[1] = 1
    type: Literal["vision_state"] = "vision_state"
    timestampMs: int = Field(..., ge=0)
    mode: VisionMode
    confidence: float = Field(..., ge=0.0, le=1.0)
    targetBox: BoundingBox | None = None
    reason: str


class CommandAck(BaseModel):
    model_config = ConfigDict(frozen=True, extra="forbid")

    protocolVersion: Literal[1] = 1
    type: Literal["command_ack"] = "command_ack"
    commandId: str
    accepted: bool
    reason: str


def parse_line(line: Union[str, bytes]) -> Union[MotionIntent, VisionState, CommandAck]:
    if isinstance(line, str):
        raw_bytes = line.encode("utf-8")
        decoded = line
    else:
        raw_bytes = line
        try:
            decoded = line.decode("utf-8")
        except UnicodeDecodeError:
            raise ValidationError.from_exception_data("ProtocolMessage", line_errors=[])

    if len(raw_bytes) > 4096:
        raise ValidationError.from_exception_data("ProtocolMessage", line_errors=[])

    try:
        data = json.loads(decoded)
        if not isinstance(data, dict):
            raise ValidationError.from_exception_data("ProtocolMessage", line_errors=[])

        type_str = data.get("type")
        if type_str == "motion_intent":
            return MotionIntent.model_validate(data)
        elif type_str == "vision_state":
            return VisionState.model_validate(data)
        elif type_str == "command_ack":
            return CommandAck.model_validate(data)
        else:
            raise ValidationError.from_exception_data("ProtocolMessage", line_errors=[])
    except Exception as e:
        if isinstance(e, ValidationError):
            raise e
        raise ValidationError.from_exception_data("ProtocolMessage", line_errors=[]) from e
