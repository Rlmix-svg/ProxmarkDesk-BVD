# ProxmarkDesk Android 1.9 — second architecture transfer

## Исправлено

- активная команда больше не вытесняется новым `submit()`;
- timeout не освобождает поток до прихода marker предыдущей команды;
- Stop ждёт синхронизацию и не убивает USB/клиент;
- устранён stale capability/UID-контекст при новом поиске;
- Stop в Sniff/Emulation использует общий Command Session Manager;
- пять основных разделов заменили длинную строку legacy-вкладок;
- восстановлена тёмная базовая палитра shell.

## Перенесено в Action Registry

- диагностика `hw version/status/tune`;
- Lua envcheck/list;
- LF read/search buffer;
- управляемое `data save`;
- Classic full dump с `classic-keys.bin`;
- start/view Sniff по основным протоколам;
- BVD ISO14443-A trace save.

## Проверки

- Command Session Manager: 22 проверки, включая busy/timeout/cancel synchronization;
- Action Registry: 65 проверок;
- source regression: пять основных областей, Action IDs, stale capability guard, UTF-8 scan;
- Python Android ELF/runtime и Python tools сохраняются в проверках.

Физические Android/USB/RF испытания этим host-safe набором не заменяются.
