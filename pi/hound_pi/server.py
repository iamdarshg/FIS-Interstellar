import select
import socket
import sys
import time
from typing import Optional
from hound_pi.agent import AgentEngine


class Server:
    def __init__(self, engine: Optional[AgentEngine] = None):
        self.engine = engine or AgentEngine()
        self.running = False

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

    def stop(self) -> None:
        self.running = False
