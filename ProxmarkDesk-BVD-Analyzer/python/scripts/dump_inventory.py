"""Размеры и SHA-256 всех дампов; метка и USB не требуются."""
from pm3 import library_path
from pathlib import Path
from datetime import datetime
import hashlib, json
root = Path(library_path())
rows = []
for file in sorted((root / "dumps").glob("*")):
    if not file.is_file():
        continue
    with file.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    row = {"file": file.name, "bytes": file.stat().st_size, "sha256": digest}
    rows.append(row)
    print(row["file"], row["bytes"], digest)
folder = root / "reports"
folder.mkdir(exist_ok=True)
out = folder / ("inventory-" + datetime.now().strftime("%Y%m%d-%H%M%S-%f") + ".json")
out.write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
print("Файлов:", len(rows), "Отчёт:", out)
