import uuid
from typing import Union
from hound_pi.policy import StationaryPolicy
from hound_pi.protocol import CommandAck, VisionState, parse_line
from hound_pi.transport import LineTransport


class AgentEngine:
    def __init__(self, policy=None):
        self.policy = policy or StationaryPolicy()

    def process_line(self, line: Union[str, bytes]) -> str:
        try:
            parsed = parse_line(line)
            if isinstance(parsed, VisionState):
                motion_intent = self.policy.evaluate(parsed)
                return motion_intent.model_dump_json(by_alias=True)
            else:
                ack = CommandAck(
                    protocolVersion=1,
                    type="command_ack",
                    commandId=str(uuid.uuid4()),
                    accepted=False,
                    reason="INVALID_JSON"
                )
                return ack.model_dump_json(by_alias=True)
        except Exception:
            ack = CommandAck(
                protocolVersion=1,
                type="command_ack",
                commandId=str(uuid.uuid4()),
                accepted=False,
                reason="INVALID_JSON"
            )
            return ack.model_dump_json(by_alias=True)

    def run(self, transport: LineTransport) -> None:
        for line in transport.read_lines():
            response = self.process_line(line)
            transport.write_line(response)
