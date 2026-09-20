# ProxmarkDesk BVD 1.10.6 — Static Zlib Fix

- Termux native-сборка Proxmark3 теперь запрашивает статическую zlib через `ZLIB_USE_STATIC_LIBS=ON`.
- Это предотвращает появление непереносимой зависимости `libz.so.1` в Android APK.
- Проверка DT_NEEDED по-прежнему отклоняет любые Termux-private библиотеки.
- Остальные изменения 1.10.4/1.10.5 сохранены: `PMDESK_PIPE=1`, live timer, protocol dictionaries, context actions.
