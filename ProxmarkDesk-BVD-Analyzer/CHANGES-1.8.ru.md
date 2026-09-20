# ProxmarkDesk Android 1.8 — architecture base

Сборка подготовлена как безопасный переход от рабочего BVD 1.6 к целевой архитектуре из итогового отчёта.

Основные изменения:

- исправлена пропажа header/кнопок на вкладке «Чтение»;
- быстрые действия чтения переведены на stable Action ID;
- добавлен `ActionExecutor`;
- расширен `ActionRegistry` для основных read/NDEF/dump действий;
- обязательные параметры command template теперь проверяются строго;
- часть `readBlock` / `readNdef` / `dumpTag` переведена через Registry/CLI compatibility;
- Termux builder больше не требует отдельный пакет `zipalign`;
- добавлены regression-тесты исходника и Action Registry.

Подробности: `ARCHITECTURE-1.8.ru.md`.
