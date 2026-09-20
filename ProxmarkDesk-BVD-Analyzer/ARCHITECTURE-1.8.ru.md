# ProxmarkDesk BVD 1.8 — architecture base

Эта сборка является консервативным архитектурным шагом от рабочего 1.6 к целевой схеме из итогового отчёта. Она не переписывает интерфейс целиком и не удаляет старые экраны, чтобы не потерять уже работающий функционал.

## Внесено

- Исправлена вкладка «Чтение»: header ListView получает adapter до ранних `return`, поэтому кнопки «Авто / Поиск HF / Поиск LF» не исчезают на пустом или занятом состоянии.
- Быстрые кнопки «Чтение» больше не формируют команды напрямую: они вызывают стабильные action id через `ActionExecutor`.
- `ActionRegistry` получил поиск по стабильному id (`findById`) и строгую проверку обязательных параметров: незаполненный `{token}` теперь считается ошибкой, а не молча удаляется.
- В Action Registry добавлены основные операции текущего чтения: auto search, Classic/MFU/ISO15693 block read, NDEF Classic/MFU, MFU/ISO15693/T55xx dump.
- `readBlock`, `readNdef` и не-Classic ветки `dumpTag` переведены на `ActionExecutor -> ActionRegistry -> CliCompat -> CommandSessionManager`.
- Classic full dump пока оставлен в старом пути, потому что перед командой выполняется дополнительная проверка размера `classic-keys.bin`; перенос этой проверки должен идти отдельным изменением.
- Добавлены regression-тесты Action Registry и исходника ReadingPage.
- Termux build scripts обновлены под фактический репозиторий Termux, где `zipalign` может отсутствовать как отдельный пакет. Если `zipalign` присутствует — он используется; если нет — сборка не блокируется, а APK всё равно подписывается и проверяется `apksigner`.

## Намеренно не включено в этот шаг

- Полный переход на пять новых экранов.
- Удаление всех legacy-методов `MainActivity`.
- Полная модель `SavedCard` и внутренняя БД библиотеки.
- Универсальный Dump Viewer по Protocol Adapter.
- Перевод всех Sniff/BVD/Lua/Python/эмуляционных кнопок на Action Registry.

Эти изменения затрагивают слишком много независимых сценариев для одной безопасной миграции и должны переноситься по одному с regression-тестами.

## Основной поток новых действий

```text
UI
  -> ActionExecutor(actionId, params)
  -> ActionRegistry
  -> capability/session availability
  -> CliCompat
  -> MainActivity.run(...)
  -> ClientService
  -> CommandSessionManager
  -> embedded Iceman / Proxmark3
```

## Сборка в Termux

Из корня `termux_builder_package`:

```bash
./check-env.sh
./build-termux.sh
```

Готовый APK:

```text
out/ProxmarkDesk-Android-arm64.apk
```
