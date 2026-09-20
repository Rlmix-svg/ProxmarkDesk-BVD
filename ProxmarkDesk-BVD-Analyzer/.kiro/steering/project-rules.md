# ProxmarkDesk-BVD Project Rules

Работай максимально автономно.

Не задавай пользователю дополнительные вопросы, если решение можно принять на основании:
- текущего кода;
- структуры проекта;
- документации внутри проекта;
- ошибок сборки;
- существующих тестов;
- логов выполнения.

Перед любыми изменениями сначала изучи связанные файлы и текущую реализацию.

## Главные правила

1. Не переписывай рабочие части проекта без необходимости.
2. Не меняй архитектуру проекта только ради "улучшения".
3. Сохраняй совместимость с существующей логикой.
4. Не удаляй существующий функционал без явной необходимости.
5. Не переименовывай публичные классы, методы, файлы и ресурсы без причины.
6. Не обновляй зависимости и инструменты сборки без необходимости.
7. Не меняй AndroidManifest.xml без проверки последствий.
8. Не выполняй разрушительные Git-команды.
9. Не удаляй пользовательские файлы и исходники.
10. Не завершай задачу после первой ошибки.

## Порядок работы

При получении задачи:

1. Изучи структуру проекта.
2. Найди все файлы, связанные с задачей.
3. Прочитай существующую реализацию.
4. Определи минимальный набор необходимых изменений.
5. Внеси изменения.
6. Проверь синтаксис и зависимости.
7. Запусти подходящую проверку или сборку.
8. Если есть ошибка:
   - прочитай полный текст ошибки;
   - найди первопричину;
   - исправь её;
   - повтори проверку.
9. Продолжай цикл исправлений до успешного результата либо до обнаружения внешней проблемы, которую нельзя устранить средствами проекта.

## Сборка проекта

Основной сценарий:

```text
python build.py
```

## Сборочное окружение Windows

Используй следующие фиксированные пути и не выполняй повторный поиск SDK/JDK/NDK/CMake/Ninja без необходимости.

- JDK:
  `C:\Program Files\Android\Android Studio\jbr`

- Android SDK:
  `C:\Users\irz\AppData\Local\Android\Sdk`

- Android API 36:
  `C:\Users\irz\AppData\Local\Android\Sdk\platforms\android-36\android.jar`

- Android Build Tools 36.0.0:
  `C:\Users\irz\AppData\Local\Android\Sdk\build-tools\36.0.0`

- Android NDK:
  `C:\Users\irz\AppData\Local\Android\Sdk\ndk\28.0.13004108`

- CMake:
  `C:\Users\irz\AppData\Local\Android\Sdk\cmake\4.1.2\bin\cmake.exe`

- Ninja:
  `C:\Users\irz\AppData\Local\Android\Sdk\cmake\4.1.2\bin\ninja.exe`

- ADB:
  `C:\Users\irz\AppData\Local\Android\Sdk\platform-tools\adb.exe`

- Native source Proxmark3 v4.21611:
  `..\proxmark3-git`

- Git Bash:
  `C:\Program Files\Git\bin\sh.exe`

- GNU Make:
  `C:\Program Files (x86)\GnuWin32\bin\make.exe`

При сборке проекта используй эти пути напрямую.

Не выполняй повторное сканирование дисков для поиска Java, Android SDK, NDK, CMake или Ninja, если указанные пути существуют.

## Полная команда сборки Windows

```powershell
python build.py `
  --jdk "C:\Program Files\Android\Android Studio\jbr" `
  --android-jar "C:\Users\irz\AppData\Local\Android\Sdk\platforms\android-36\android.jar" `
  --build-tools "C:\Users\irz\AppData\Local\Android\Sdk\build-tools\36.0.0" `
  --ndk "C:\Users\irz\AppData\Local\Android\Sdk\ndk\28.0.13004108" `
  --native-source "..\proxmark3-git" `
  --cmake "C:\Users\irz\AppData\Local\Android\Sdk\cmake\4.1.2\bin\cmake.exe" `
  --ninja "C:\Users\irz\AppData\Local\Android\Sdk\cmake\4.1.2\bin\ninja.exe"
```

