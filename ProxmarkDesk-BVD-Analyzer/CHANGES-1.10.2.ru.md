# ProxmarkDesk BVD 1.10.2 — Protocol Dictionaries

- MIFARE Classic: контекстная проверка стандартных ключей переведена с `hf mf chk` на быстрый `hf mf fchk`.
- Для Classic добавлен импорт собственного словаря и запуск `hf mf fchk --<size> -f <dict>`.
- Добавлены контекстные словари для команд Iceman v4.21611: MIFARE Plus, Ultralight C, Ultralight AES, DESFire, iCLASS, T55xx, Hitag, EM4x05 и EM4x50.
- Словарь хранится во внутреннем каталоге приложения отдельно для каждого действия; UI показывает имя выбранного файла.
- Ultralight C/AES словари появляются только для подтверждённого соответствующего subtype.
- Расширено распознавание TagInfo для MIFARE Plus, Hitag, EM4x05 и EM4x50.
- Мягкая кнопка Stop не возвращена; аварийное прерывание остаётся через «Остановить сеанс».
