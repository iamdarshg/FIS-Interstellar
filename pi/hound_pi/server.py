import json
import argparse
import socket
import sys
import time
import uuid
import threading
from pathlib import Path
from typing import Any, Optional
from aiohttp import web
from hound_pi.agent import AgentEngine
from hound_pi.dashboard_html import get_dashboard_html
from hound_pi.ota import update_from_git
from hound_pi.protocol import LocationCommand, Object2D
from hound_pi.radar_ld1125h import HLKLD1125HRadar
from hound_pi.spi_driver import SPIRoverController


class Server:
    def __init__(
        self,
        engine: Optional[AgentEngine] = None,
        radar: Optional[HLKLD1125HRadar] = None,
    ) -> None:
        self.engine = engine or AgentEngine()
        if self.engine.spi_controller is None:
            self.engine.spi_controller = SPIRoverController()
        self.radar = radar or HLKLD1125HRadar()
        self.running = False
        self.objects_2d: list[Object2D] = []

    def run_stdstream_server(self) -> None:
        """Run line-delimited stdstream server."""
        self.running = True
        while self.running:
            line = sys.stdin.readline()
            if not line:
                break
            line_str = line.strip()
            if line_str:
                response = self.engine.process_line(line_str)
                sys.stdout.write(response + "\n")
                sys.stdout.flush()

    def start_tcp_server(self, host: str, port: int, idle_timeout_sec: float = 5.0) -> None:
        """Start TCP server with reconnection resilience and 5s idle timeout."""
        server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server_sock.bind((host, port))
        server_sock.listen(1)
        server_sock.settimeout(0.5)

        self.running = True

        try:
            while self.running:
                try:
                    client_sock, client_addr = server_sock.accept()
                except socket.timeout:
                    continue
                except OSError:
                    break

                client_sock.settimeout(0.5)
                last_active_time = time.time()
                buffer = ""

                while self.running:
                    try:
                        data = client_sock.recv(1024)
                        if not data:
                            break

                        last_active_time = time.time()
                        buffer += data.decode("utf-8", errors="replace")

                        while "\n" in buffer:
                            line, buffer = buffer.split("\n", 1)
                            line_str = line.strip()
                            if line_str:
                                resp = self.engine.process_line(line_str)
                                client_sock.sendall((resp + "\n").encode("utf-8"))

                    except socket.timeout:
                        if time.time() - last_active_time > idle_timeout_sec:
                            break
                        continue
                    except (OSError, ConnectionResetError):
                        break

                try:
                    client_sock.close()
                except Exception:
                    pass

        finally:
            server_sock.close()

    def create_web_app(self) -> web.Application:
        app = web.Application()
        app.router.add_get("/", self._handle_dashboard)
        app.router.add_get("/api/state", self._handle_get_state)
        app.router.add_get("/api/map", self._handle_get_map)
        app.router.add_get("/api/radar", self._handle_get_radar)
        app.router.add_post("/api/control/location", self._handle_location_cmd)
        app.router.add_post("/api/control/motion", self._handle_motion_cmd)
        app.router.add_post("/api/vision/detection", self._handle_detection)
        app.router.add_post("/api/map/reset", self._handle_map_reset)
        return app

    async def _handle_get_radar(self, request: web.Request) -> web.Response:
        detection = self.radar.get_last_detection()
        return web.json_response({"status": "ok", "radar": detection})


    async def _handle_dashboard(self, request: web.Request) -> web.Response:
        html = get_dashboard_html()
        return web.Response(text=html, content_type="text/html")

    async def _handle_get_state(self, request: web.Request) -> web.Response:
        spi_info = {
            "connected": self.engine.spi_controller is not None,
            "isHardware": (
                self.engine.spi_controller.is_hardware if self.engine.spi_controller else False
            ),
        }
        return web.json_response({
            "protocolVersion": 1,
            "status": "OK",
            "spi": spi_info,
            "objectCount": len(self.objects_2d),
        })

    async def _handle_get_map(self, request: web.Request) -> web.Response:
        rover_x = 0.0
        rover_y = 0.0
        rover_heading = 0.0

        if self.engine.latest_map_state is not None:
            rover_x = self.engine.latest_map_state.roverX
            rover_y = self.engine.latest_map_state.roverY
            rover_heading = self.engine.latest_map_state.roverHeading
            objs = [o.model_dump() for o in self.engine.latest_map_state.objects]
        else:
            objs = [o.model_dump() for o in self.objects_2d]

        map_data = {
            "protocolVersion": 1,
            "type": "map_state",
            "timestampMs": int(time.time() * 1000),
            "roverX": rover_x,
            "roverY": rover_y,
            "roverHeading": rover_heading,
            "objects": objs,
        }
        return web.json_response(map_data)

    async def _handle_location_cmd(self, request: web.Request) -> web.Response:
        try:
            data = await request.json()
            target_x = float(data.get("targetX", data.get("x", 0.0)))
            target_y = float(data.get("targetY", data.get("y", 0.0)))
            speed = float(data.get("speed", 1.0))
            reason = str(data.get("reason", "web_ap_location_command"))
            cmd_id = str(data.get("id", uuid.uuid4()))

            loc_cmd = LocationCommand(
                protocolVersion=1,
                type="location_command",
                id=cmd_id,
                sentAtMs=int(time.time() * 1000),
                targetX=target_x,
                targetY=target_y,
                speed=speed,
                reason=reason,
            )

            res_json_str = self.engine.process_line(loc_cmd.model_dump_json())
            res_data: dict[str, Any] = json.loads(res_json_str)
            return web.json_response(res_data)
        except Exception as e:
            return web.json_response(
                {
                    "protocolVersion": 1,
                    "type": "command_ack",
                    "commandId": str(uuid.uuid4()),
                    "accepted": False,
                    "reason": f"INVALID_LOCATION_PAYLOAD: {e}",
                },
                status=400,
            )

    async def _handle_motion_cmd(self, request: web.Request) -> web.Response:
        try:
            data = await request.json()
            intent = str(data.get("intent", "STOP"))
            dur_ms = int(data.get("durationMs", 200))
            if self.engine.spi_controller is not None:
                spi_res = self.engine.spi_controller.send_motion(
                    intent=intent, duration_ms=dur_ms
                )
            else:
                spi_res = {"accepted": False, "reason": "NO_SPI"}
            return web.json_response({"status": "ok", "intent": intent, "spi": spi_res})
        except Exception as e:
            return web.json_response({"status": "error", "reason": str(e)}, status=400)

    async def _handle_detection(self, request: web.Request) -> web.Response:
        try:
            data = await request.json()
            obj_id = str(data.get("id", uuid.uuid4()))
            label = str(data.get("label", "Object"))
            x = float(data.get("x", 0.0))
            y = float(data.get("y", 0.0))
            conf = float(data.get("confidence", 0.9))
            dist = float(data.get("distance", (x**2 + y**2) ** 0.5))
            ang = float(data.get("angle", 0.0))

            obj = Object2D(
                id=obj_id,
                label=label,
                x=x,
                y=y,
                confidence=conf,
                distance=dist,
                angle=ang,
                lastSeenMs=int(time.time() * 1000),
            )
            self.objects_2d = [o for o in self.objects_2d if o.id != obj_id]
            self.objects_2d.append(obj)

            return web.json_response({"status": "ok", "object": obj.model_dump()})
        except Exception as e:
            return web.json_response({"status": "error", "reason": str(e)}, status=400)

    async def _handle_map_reset(self, request: web.Request) -> web.Response:
        self.objects_2d.clear()
        self.engine.latest_map_state = None
        return web.json_response({"status": "ok", "message": "Map reset"})

    def start_web_server(self, host: str = "0.0.0.0", port: int = 8765) -> None:
        app = self.create_web_app()
        web.run_app(app, host=host, port=port)

    def start_services(
        self,
        web_host: str = "0.0.0.0",
        web_port: int = 8765,
        tcp_host: str = "0.0.0.0",
        tcp_port: int = 8766,
        update_on_start: bool = False,
        repo_dir: str | None = None,
        branch: str = "main",
    ) -> None:
        if update_on_start:
            update_from_git(repo_dir or Path(__file__).resolve().parents[2], branch=branch)

        tcp_thread = threading.Thread(
            target=self.start_tcp_server,
            args=(tcp_host, tcp_port),
            daemon=True,
            name="hound-pi-tcp",
        )
        tcp_thread.start()
        self.start_web_server(web_host, web_port)

    def stop(self) -> None:
        self.running = False


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="HOUND Pi server")
    parser.add_argument("--web-host", default="0.0.0.0")
    parser.add_argument("--web-port", type=int, default=8765)
    parser.add_argument("--tcp-host", default="0.0.0.0")
    parser.add_argument("--tcp-port", type=int, default=8766)
    parser.add_argument("--no-tcp", action="store_true")
    parser.add_argument("--no-web", action="store_true")
    parser.add_argument("--update-on-start", action="store_true")
    parser.add_argument("--repo-dir", default=None)
    parser.add_argument("--branch", default="main")
    args = parser.parse_args(argv)

    server = Server()
    if args.update_on_start:
        update_from_git(args.repo_dir or Path(__file__).resolve().parents[2], branch=args.branch)

    if args.no_web and args.no_tcp:
        parser.error("At least one server must be enabled")

    if not args.no_web and not args.no_tcp:
        server.start_services(
            web_host=args.web_host,
            web_port=args.web_port,
            tcp_host=args.tcp_host,
            tcp_port=args.tcp_port,
            update_on_start=False,
            repo_dir=args.repo_dir,
            branch=args.branch,
        )
        return 0

    if not args.no_tcp:
        tcp_thread = threading.Thread(
            target=server.start_tcp_server,
            args=(args.tcp_host, args.tcp_port),
            daemon=False,
        )
        tcp_thread.start()

    if not args.no_web:
        server.start_web_server(args.web_host, args.web_port)
        return 0

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
