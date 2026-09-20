"""Сведения Proxmark3 через текущий сеанс, без изменения метки."""
from pm3 import pm3, library_path
from pathlib import Path
from datetime import datetime
p = pm3()
rc = p.console("hw version")
print(p.grabbed_output)
folder = Path(library_path()) / "reports"
folder.mkdir(exist_ok=True)
out = folder / ("device-python-" + datetime.now().strftime("%Y%m%d-%H%M%S-%f") + ".txt")
out.write_text(p.grabbed_output, encoding="utf-8")
print("Отчёт сохранён:", out)
if rc:
    raise SystemExit(rc)
