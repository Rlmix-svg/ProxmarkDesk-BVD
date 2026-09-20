"""ProxmarkDesk adapter for the public console/grabbed_output PM3 interface.

Uses the app's existing Iceman session, never opens USB independently.
This is not the upstream _pm3 SWIG extension.
"""
import json
import os
import socket
import threading

_lock = threading.Lock()

def _request(payload):
    payload = dict(payload, token=os.environ["PMDESK_TOKEN"])
    with _lock, socket.create_connection(("127.0.0.1", int(os.environ["PMDESK_PORT"])), timeout=10) as sock:
        sock.settimeout(86410)
        sock.sendall((json.dumps(payload, ensure_ascii=False) + "\n").encode("utf-8"))
        with sock.makefile("rb") as incoming:
            line = incoming.readline(12 * 1024 * 1024 + 1)
        if not line or len(line) > 12 * 1024 * 1024:
            raise ConnectionError("Ответ ProxmarkDesk отсутствует или превышает предел")
        reply = json.loads(line)
        if not reply.get("ok"):
            raise RuntimeError(reply.get("error", "Ошибка связи с ProxmarkDesk"))
        return reply

class pm3:
    def __init__(self, port=None):
        if port not in (None, ""):
            raise ValueError("Выбор USB-порта выполняется на вкладке Устройство")
        self.grabbed_output = ""
        self.name = "ProxmarkDesk / Iceman"

    def console(self, cmd, capture=True, quiet=True, timeout=300):
        reply = _request({"action": "command", "command": str(cmd), "timeout": int(timeout)})
        self.grabbed_output = reply["output"] if capture else ""
        if not quiet:
            print(reply["output"], end="" if reply["output"].endswith("\n") else "\n")
        return reply["returncode"]

def library_path():
    """Directory containing dumps, reports and this device's operation history."""
    return os.environ["PMDESK_LIBRARY"]
