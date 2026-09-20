"""Проверка встроенного Python и стандартных модулей; устройство не требуется."""
import sys, json, ssl, sqlite3, hashlib, struct, zlib, bz2, lzma, ctypes
from pathlib import Path
from tempfile import TemporaryDirectory
print("Python:", sys.version)
sample = "Проверка ProxmarkDesk".encode()
assert zlib.decompress(zlib.compress(sample)) == sample
assert bz2.decompress(bz2.compress(sample)) == sample
assert lzma.decompress(lzma.compress(sample)) == sample
assert struct.unpack("<I", struct.pack("<I", 123456)) == (123456,)
with sqlite3.connect(":memory:") as db:
    assert db.execute("select 2 + 2").fetchone()[0] == 4
with TemporaryDirectory() as tmp:
    path = Path(tmp) / "тест.json"
    path.write_text(json.dumps({"ok": True}), encoding="utf-8")
    assert json.loads(path.read_text(encoding="utf-8"))["ok"]
print("OpenSSL:", ssl.OPENSSL_VERSION)
print("SQLite:", sqlite3.sqlite_version)
print("SHA256:", hashlib.sha256(sample).hexdigest())
print("PASS: JSON, UTF-8, файлы, SHA256, SQLite, struct, zlib, bz2, lzma, ctypes, SSL")
