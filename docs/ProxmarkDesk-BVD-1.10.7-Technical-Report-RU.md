# ProxmarkDesk BVD 1.10.7 — техническое описание, архитектура и результаты тестирования

## 1. Назначение проекта

ProxmarkDesk BVD — самостоятельное Android-приложение для работы с Proxmark3 со смартфона через USB OTG. Основная архитектурная идея проекта — не переносить протоколы Proxmark3 в Java заново, а запускать внутри APK нативный Proxmark3/Iceman client для Android ARM64 и строить над ним Android UI, библиотеку файлов, автоматизацию операций, анализ результатов и обновление firmware.

Текущая версия APK: **1.10.7**. Android package: `org.proxmarkdesk.android`. `minSdkVersion=30`, `targetSdkVersion=36`. Приложение требует USB host. Основной целевой профиль, реально использованный при тестировании: **Proxmark3 Easy / PM3GENERIC / AT91SAM7S512**.

Встроенный клиент мигрирован с Iceman v4.21611 на **Iceman v4.23346**.

## 2. Общая архитектура

```text
Android UI / MainActivity
        │
        ├── Карта / ReadingPage
        ├── Операции / OperationPage
        ├── Библиотека / DumpLibrary + LibraryMirror
        ├── Инструменты
        │     ├── Console
        │     ├── Sniff / traces
        │     ├── PM3 Commands
        │     ├── Emulation
        │     ├── BVD Analyzer
        │     ├── Signals
        │     ├── Lua sequences
        │     ├── Python
        │     └── Device information
        └── Firmware updater
                 │
Action / capability layer
ActionRegistry + CapabilityEngine + ActionExecutor
                 │
ClientService + CommandSessionManager
                 │
native Proxmark3/Iceman ARM64 client
                 │
/dev/ttyACM0 / USB OTG / root
                 │
Proxmark3
```

`ClientService` является центральной службой. Она управляет native client, состоянием USB и сессии, выполнением команд, console output, журналами, ресурсами, Python bridge, firmware updater и повторным подключением.

Для взаимодействия приложения с native client используется `PMDESK_PIPE=1`. Native client запускается с параметрами типа `--incognito --flush --port /dev/ttyACM0`.

Приложение не реализует отдельный Java serial stack для команд PM3: реальный обмен с устройством выполняет upstream Proxmark3 client через Linux character device `/dev/ttyACM0`.

## 3. Основная навигация

Верхняя постоянная навигация содержит четыре раздела:

| Раздел | Назначение |
|---|---|
| **Карта** | обнаружение HF/LF метки, определение семейства, подбор доступных команд |
| **Операции** | контекстные действия только для текущей подтверждённой карты |
| **Библиотека** | дампы, импорт, анализ, экспорт и управление файлами |
| **Инструменты** | USB, консоль, sniff, полный PM3-каталог, эмуляция, BVD, сигналы, Lua, Python и информация |

Состояние клиента отображается отдельным индикатором: READY/online, offline либо текущий текст состояния. UI обновляет состояние службы и progress приблизительно каждые 500 мс.

## 4. Экран «Карта»

Экран построен классом `ReadingPage`.

Основные быстрые действия:

- **Авто** — автоматический поиск;
- **Поиск HF**;
- **Поиск LF**;
- отображение обнаруженной карты и UID;
- полные сведения о карте;
- переход в «Действия для этой карты»;
- ручной выбор семейства, если автоматическое определение неоднозначно.

После определения карты каталог действий фильтруется по её семейству. Доступен поиск по командам, Lua/Python-скриптам и русским описаниям.

Фильтры каталога:

- Все;
- Команда;
- Lua;
- Python;
- специальные варианты;
- только избранное.

Для каждой команды можно открыть параметры, `-h`, исходный код Lua/Python, добавить действие в избранное и выполнить его.

## 5. Экран «Операции»

`OperationPage` — контекстный UI над `ActionRegistry` и `CardCapability`.

Он не использует старую сохранённую карту как текущую: перед потенциально чувствительной операцией требуется подтверждённое текущее обнаружение.

Экран показывает:

- subtype/семейство;
- UID;
- количество секторов;
- объём памяти;
- наличие ключей;
- частичный доступ;
- статус capability;
- быстрые переходы **Ключи** и **Сниффер**.

Действия группируются по категориям:

- «Чтение и информация»;
- «Ключи и аутентификация»;
- «Словари / ключи»;
- «Захват и эмуляция»;
- другие операции.

В `ActionRegistry` описаны десятки логических действий, в том числе MIFARE Classic, Ultralight/NTAG, ISO15693, DESFire, ISO14443-B, FeliCa, iCLASS/PicoPass, LEGIC, Topaz, EM410x, HID Prox, T55xx, MIFARE Plus и dictionary-based checks.

Примеры реализованных действий:

- Classic standard-key check;
- Classic dictionary check;
- nested;
- autopwn;
- Classic dump/read block;
- Classic emulation;
- MFU info/dump/password dump/PWD_AUTH/emulation;
- ISO15693 info/dump/emulation;
- DESFire info;
- FeliCa info;
- iCLASS info;
- LEGIC info;
- Topaz info;
- LF EM/HID reader;
- T55xx info;
- проверки пользовательских словарей для MFP, MFU, DESFire, iCLASS, T55xx, Hitag, EM4x05 и EM4x50.

`CapabilityEngine` и ограничения `ActionDef` определяют, когда действие допустимо. Параметры (UID, block, sector, size, dump path, dictionary path) могут автоматически предлагаться из текущего состояния карты.

## 6. «Устройство / USB»

Экран содержит:

- последовательное устройство, по умолчанию `/dev/ttyACM0`;
- переключатель root-доступа к USB;
- **Найти USB / tty-порты**;
- **Офлайн: без устройства**;
- **Остановить сеанс**;
- **Экспорт диагностики USB**;
- **Обновить устройство**;
- выбор профиля устройства;
- выбор папки зеркала в Downloads;
- резервное копирование библиотеки;
- лицензии и инструкцию.

Поддерживается автоматическое подключение при старте приложения и при USB attach event.

В протестированной конфигурации Proxmark3 определялся как CDC ACM и был доступен через `/dev/ttyACM0`.

## 7. Консоль Iceman

Консоль предоставляет прямой доступ к встроенному CLI.

Функции:

- ввод произвольной команды Iceman;
- проверка корректности ввода;
- выполнение;
- live console output;
- цветовое выделение команд;
- progress bar для длительных операций;
- отображение времени выполнения;
- экспорт консоли;
- сохранение draft-команды.

Таким образом, отсутствие отдельной GUI-кнопки не закрывает доступ к функциям встроенного Proxmark3 client.

## 8. PM3 Commands / runtime catalogue

Приложение умеет получать каталог непосредственно от встроенного клиента (`ClientService.catalogue()`), а не только использовать статический список.

Для v4.23346 был получен каталог из **1024 команд**. В старой версии использовалось 913 команд. При миграции было обнаружено 120 добавленных и 9 удалённых команд.

Каталог поддерживает:

- поиск;
- русские описания;
- фильтрацию по семейству;
- открытие `-h`;
- ввод параметров;
- переход в консоль.

## 9. Sniff / трассы

Поддерживаемые варианты UI:

- ISO 14443-A / Classic / NTAG;
- ISO 14443-B;
- ISO 15693;
- FeliCa;
- iCLASS;
- Topaz;
- LF signal.

Доступны:

- ручной sniff;
- auto-sniff ISO14443-A с задаваемой длительностью и задержкой;
- автоматический выбор протокола по найденной метке;
- просмотр/декодирование;
- сохранение capture;
- справка sniff;
- анализ PWD_AUTH из файла.

Auto-sniff реализует цепочку захват → таймер → `hw break` → просмотр/trace list → сохранение trace.

## 10. Библиотека, дампы и файлы

Экран «Библиотека» / «Дампы и анализ» поддерживает:

- импорт BIN / JSON / EML / signal;
- поиск по UID или имени;
- открыть и анализировать;
- эмулировать;
- экспортировать;
- переименовать;
- добавить UID в имя;
- удалить.

Оригиналы хранятся во внутренней библиотеке приложения. Дополнительно пользователь может выбрать папку через Android Storage Access Framework, после чего `LibraryMirror` создаёт структуру для дампов, трасс, сигналов, профилей, паролей, журналов, отчётов, сценариев и резервных копий.

Предусмотрена полная ZIP-резервная копия библиотеки.

## 11. Эмуляция

Экран позволяет выбрать сохранённый BIN/JSON/EML и тип:

- Classic Mini;
- Classic 1K;
- Classic 2K;
- Classic 4K;
- Ultralight;
- Ultralight EV1 / NTAG;
- Ultralight C;
- Ultralight AES;
- ISO15693.

Для обновлённого CLI v4.23346 simulation workflows были исправлены. Для Classic и MFU используется последовательность очистки/загрузки emulator memory перед `sim`; для ISO15693 — загрузка и запуск.

## 12. Сигналы и LF processing

Экран «Сигналы» предоставляет:

- получить LF-сигнал;
- открыть сохранённый сигнал;
- `lf config`;
- декодировать LF из текущего буфера;
- сохранить signal buffer;
- raw demodulation;
- detect clock;
- autocorrelation;
- normalization;
- decimation;
- left/right trim;
- HPF;
- IIR;
- bitstream extraction;
- полный каталог `data` tools.

## 13. Lua

В APK включены ресурсы Lua Proxmark3.

При миграции v4.23346 подготовлено:

- 563 resource files всего;
- 24 Lua libraries;
- 81 Lua scripts.

UI предоставляет:

- диагностику Lua environment;
- проверку/восстановление ресурсов;
- безопасную проверку загрузки Lua без RF;
- просмотр lualibs;
- просмотр luascripts;
- запуск Lua с аргументами;
- просмотр исходного кода;
- сохранённые последовательности CLI-команд `.cmd`.

Сценарии `.cmd` выполняются последовательно; каждый шаг ожидает предыдущий. Можно выбрать политику остановки после предупреждения/ошибки.

## 14. Встроенный Python

В текущих исходниках включён **CPython 3.14.7 ARM64**, работающий внутри APK без Termux.

Встроенные скрипты:

1. `python_selftest.py`;
2. `device_report.py`;
3. `dump_inventory.py`;
4. `pm3_eml2mfd.py`;
5. `pm3_mfd2eml.py`;
6. `pm3_nfc2eml.py`;
7. `findbits.py`;
8. `parity.py`;
9. `xorcheck.py`;
10. `pm3_help2json.py`;
11. `pm3_help2list.py`.

Поддерживаются:

- запуск встроенного скрипта;
- аргументы и timeout;
- импорт собственного `.py`;
- просмотр исходного кода;
- `input()` через Android dialog;
- отчёты;
- Python licenses;
- адаптер `pm3`, позволяющий передавать команды в текущую сессию Iceman.

Не заявляются как поддерживаемые: pip, произвольные сторонние пакеты и полный native SWIG API.

## 15. BVD Analyzer

Отдельный модуль предназначен для исследовательского сравнения NTAG/Ultralight dump/profile и trace.

Функции:

- создать профиль из сохранённого дампа;
- импортировать dump;
- указать результат внешней проверки: «Не проверена / Рабочая / Не открывает»;
- сохранить известные PWD/PACK;
- сравнить профили;
- импортировать trace A;
- сравнить trace A и B;
- сохранить текущую HF trace;
- отчёт;
- JSON export;
- удаление профиля.

Анализатор не считает отдельные UID/PWD/страницы доказательством прав доступа и не делает автоматических выводов о причине принятия/отказа внешним считывателем.

## 16. История и результаты

`ResultCache` сохраняет metadata и полный/частичный вывод операций.

Поиск истории поддерживает UID, команду, описание и текст результата.

Для записи доступны:

- status;
- command;
- UID;
- device;
- start time;
- log.

Из истории можно открыть результат, экспортировать полный output и повторить команду с параметрами.

## 17. Firmware updater

Updater встроен в экран устройства.

Формат ZIP:

```text
firmware.properties
bootrom.elf
fullimage.elf
```

`FirmwarePackage` проверяет metadata, SHA-256 и ELF structure. Целевой профиль текущей реализации:

```text
format=1
platform=PM3GENERIC
chip=AT91SAM7S
version=v4.23346
```

Обычный режим 1.10.7 реализован как:

```text
bootrom
  ↓
USB re-enumeration
  ↓
fullimage
  ↓
USB re-enumeration
  ↓
hw version
  ↓
проверка Bootrom + OS
```

Сохранён recovery mode «только bootrom».

Перед прошивкой пользователь отдельно подтверждает, что подключён Proxmark3 Easy / AT91SAM7S и пакет происходит из доверенной PM3GENERIC-сборки.

## 18. Миграция Iceman v4.21611 → v4.23346

Были обновлены native client, resources и compatibility layer.

Примеры исправлений CLI:

```text
hf mfu info --noauth  →  hf mfu info
hf mfu cchk -f ...    →  hf mfu chk -f ...
hf mfu aeschk -f ...  →  hf mfu chk -f ...
```

Также были исправлены simulation workflows Classic, MFU и ISO15693 и устранена ошибка обработки префикса `sc`, мешавшая командам `script...`.

## 19. Native build

Нативный client v4.23346 собран для Android/aarch64.

Проверенные `DT_NEEDED`:

```text
libm.so
libc++_shared.so
libdl.so
libc.so
```

OpenJPEG не является динамической зависимостью.

Сборка использует CMake/Ninja/Clang и linker option для Android 16 KiB pages:

```text
-Wl,-z,max-page-size=16384
```

## 20. APK build pipeline

```text
Proxmark3 v4.23346 source
        ↓
CMake / Ninja / Clang
        ↓
Android ARM64 native client
        ↓
prepare-resources.py
        ↓
Java sources
        ↓
OpenJDK / Android API 36
        ↓
D8
        ↓
AAPT2
        ↓
APK packaging
        ↓
zipalign / apksigner
```

Java source compatibility: `-source 8 -target 8`.

## 21. Реально выполненные положительные тесты

На реальном Proxmark3 Easy / AT91SAM7S512 подтверждены:

1. Android ARM64 Iceman v4.23346 запускается.
2. USB OTG обнаруживает Proxmark3.
3. Доступен `/dev/ttyACM0`.
4. Новый client связывается с устройством.
5. `hw version` выполняется.
6. `fullimage.elf` v4.23346 успешно прошит из Android updater.
7. Во время прошивки устройство исчезает и повторно появляется по USB.
8. После fullimage устройство снова подключается.
9. OS после первого этапа определилась как v4.23346.
10. `bootrom.elf` v4.23346 успешно прошит отдельным recovery-проходом.
11. Bootrom после прошивки определился как v4.23346.
12. Финальное состояние реального устройства:

```text
Firmware: PM3 GENERIC
Bootrom: Iceman/master/v4.23346-suspect
OS:      Iceman/master/v4.23346-suspect
uC: AT91SAM7S512 Rev A
Embedded flash: 512K
```

13. USB re-enumeration во flasher workflow работает.
14. Финальный APK 1.10.7 успешно собирается после внедрения объединённого updater workflow.

## 22. Что ещё нельзя выдавать за подтверждённый тест

Объединённая автоматическая последовательность **bootrom → reconnect → fullimage → reconnect → verify**, добавленная после успешных отдельных прошивок, ещё должна пройти отдельный полный destructive/end-to-end test на устройстве, которое действительно требует обновления.

Поддержка firmware updater в данный момент целенаправленно ограничена PM3GENERIC / AT91SAM7S / Proxmark3 Easy. Поддержку RDV4 и других targets следует добавлять только после отдельной проверки.

Также наличие команды в каталоге Iceman не означает, что конкретное оборудование Proxmark3 Easy физически поддерживает соответствующую функцию.

## 23. Основные исходные компоненты

| Компонент | Роль |
|---|---|
| `MainActivity.java` | основной UI, навигация, USB events, console, files, sniff, BVD |
| `ClientService.java` | native client lifecycle, session, commands, USB, firmware, Python |
| `ReadingPage.java` | карта и searchable command/script catalogue |
| `OperationPage.java` | capability-aware card actions |
| `ActionRegistry.java` | декларативное описание операций и CLI templates |
| `CapabilityEngine.java` | определение доступных возможностей карты |
| `CommandSessionManager.java` | состояние и последовательность выполнения |
| `TagInfo.java` | модель обнаруженной метки |
| `DumpLibrary.java` | управление dump files |
| `LibraryMirror.java` | экспорт/зеркалирование библиотеки через SAF |
| `ResultCache.java` | история, metadata и logs |
| `AutoSniff.java` | автоматизированный sniff workflow |
| `FirmwarePackage.java` | проверка firmware ZIP/SHA/ELF |
| `FirmwareUpdater.java` | flashing/reconnect/version verification |
| `PythonPage.java` / `AndroidPython.java` | embedded CPython UI/runtime |
| `PythonBridge.java` | связь Python с текущей PM3 session |
| `BvdProfiles.java` + `bvd/*` | BVD profiles, compare, trace analysis/export |
| `prepare-resources.py` | подготовка Proxmark3 resources |
| `build-termux.sh` | APK build |
| `build-native-termux.sh` | native Proxmark3 build |

## 24. Предложение дальнейшей разработки

Проект уже показывает практическую возможность использовать современный Proxmark3 client и обновлять Proxmark3 непосредственно с Android.

Предлагаемые направления дальнейшей работы:

- синхронизация Android client с upstream releases;
- автоматизированный audit CLI compatibility;
- capability detection вместо version-specific UI;
- расширение hardware profiles;
- безопасный firmware package signing;
- более строгая проверка hardware target перед flash;
- улучшение USB reconnect/recovery;
- автоматические integration tests UI → CLI;
- развитие dump/trace analysis;
- развитие Python/Lua workflows;
- подготовка воспроизводимой upstream-friendly Android build system.

Исходники текущей версии могут быть переданы разработчикам вместе с этим документом и журналами реальных тестов.
