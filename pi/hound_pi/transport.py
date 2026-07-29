import sys
from typing import Iterator


class LineTransport:
    """Reads line-delimited messages from input stream and writes to output stream."""

    def __init__(self, in_stream=None, out_stream=None):
        self.in_stream = in_stream or sys.stdin
        self.out_stream = out_stream or sys.stdout

    def read_lines(self) -> Iterator[str]:
        for line in self.in_stream:
            line_str = line.strip()
            if line_str:
                yield line_str

    def write_line(self, data: str) -> None:
        self.out_stream.write(data + "\n")
        self.out_stream.flush()
