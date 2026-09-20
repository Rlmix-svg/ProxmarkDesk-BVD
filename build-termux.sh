#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP="$ROOT/ProxmarkDesk-BVD-Analyzer"
PM3="$ROOT/proxmark3-v4.23346"
SDK_ROOT="${PMDESK_SDK_ROOT:-$(cat "$ROOT/.android-sdk-root" 2>/dev/null || printf '%s' "$HOME/.pmdesk-android-sdk") }"
SDK_ROOT="${SDK_ROOT% }"
ANDROID_JAR="$SDK_ROOT/platforms/android-36/android.jar"
BUILD="$ROOT/.termux-build"
OUTDIR="$ROOT/out"
MIN_API=30
TARGET_API=36
NATIVE_MODE="source"

usage(){
  cat <<USAGE
Usage: ./build-termux.sh [--source-native|--prebuilt-native]
  --source-native    native Proxmark3 обязан собраться из исходников; без fallback
  --prebuilt-native  использовать проверенный prebuilt native клиент
  default            source; при ошибке сборка останавливается
USAGE
}
for arg in "$@"; do
  case "$arg" in
    --source-native) NATIVE_MODE=source ;;
    --prebuilt-native) NATIVE_MODE=prebuilt ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Неизвестный аргумент: $arg" >&2; usage; exit 2 ;;
  esac
done

"$ROOT/check-env.sh"
mkdir -p "$BUILD" "$OUTDIR"
rm -rf "$BUILD/classes" "$BUILD/dex"
mkdir -p "$BUILD/classes" "$BUILD/dex"

echo "[1/8] Native Proxmark3..."
"$ROOT/build-native-termux.sh" "$NATIVE_MODE"
python "$ROOT/tools/verify-native-catalog.py" --project "$ROOT" --write-catalog

echo "[2/8] Ресурсы ProxmarkDesk..."
python "$APP/prepare-resources.py" --source "$PM3"
python - "$PM3" "$APP/assets/licenses.zip" <<'PY'
import sys, zipfile
from pathlib import Path
src, out = map(Path, sys.argv[1:])
with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
    for f in src.rglob('*'):
        if f.is_file() and f.name.lower().startswith(('license','copying','copyright','notice')):
            z.write(f, f.relative_to(src).as_posix())
PY
cp "$PM3/LICENSE.txt" "$APP/LICENSE.txt"

echo "[3/8] Java..."
mapfile -d '' JAVA_SOURCES < <(find "$APP/src" -name '*.java' -print0 | sort -z)
java "$APP/CompileApp.java" "$ANDROID_JAR" "$BUILD/classes" "${JAVA_SOURCES[@]}"
(
  cd "$BUILD/classes"
  jar cf "$BUILD/classes.jar" .
)

echo "[4/8] DEX..."
d8 --lib "$ANDROID_JAR" --min-api "$MIN_API" --output "$BUILD/dex" "$BUILD/classes.jar"

echo "[5/8] AAPT2 link..."
rm -f "$BUILD/unsigned.apk"
aapt2 link -o "$BUILD/unsigned.apk" -I "$ANDROID_JAR" \
  --manifest "$APP/AndroidManifest.xml" -A "$APP/assets" \
  --min-sdk-version "$MIN_API" --target-sdk-version "$TARGET_API"

echo "[6/8] Добавление DEX/native/Python..."
extra=()
[[ -f "$BUILD/libc++_shared.so" ]] && extra+=(--extra-lib "$BUILD/libc++_shared.so")
python "$ROOT/tools/package_apk.py" \
  --apk "$BUILD/unsigned.apk" --dex-dir "$BUILD/dex" \
  --native "$BUILD/libpm3client.so" \
  --python-native "$APP/python-native/arm64-v8a" "${extra[@]}"

echo "[7/8] 16 KiB alignment и подпись..."
rm -f "$BUILD/aligned.apk"
if command -v zipalign >/dev/null 2>&1; then
  echo "zipalign найден — выполняю выравнивание."
  zipalign -P 16 -f 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
else
  echo "zipalign отсутствует — отдельное выравнивание пропущено."
  cp "$BUILD/unsigned.apk" "$BUILD/aligned.apk"
fi
KEY="$ROOT/signing/development.keystore"
if [[ ! -f "$KEY" ]]; then
  mkdir -p "$(dirname "$KEY")"
  keytool -genkeypair -keystore "$KEY" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 3072 -validity 10000 \
    -dname 'CN=ProxmarkDesk Local Development' -noprompt
fi
APK="$OUTDIR/ProxmarkDesk-Android-arm64.apk"
rm -f "$APK" "$APK.idsig"
apksigner sign --ks "$KEY" --ks-pass pass:android --key-pass pass:android \
  --out "$APK" "$BUILD/aligned.apk"

echo "[8/8] Проверка..."
apksigner verify --verbose "$APK"
if command -v zipalign >/dev/null 2>&1; then
  zipalign -c -P 16 4 "$APK"
else
  echo "Проверка zipalign пропущена: команда отсутствует в Termux."
fi
sha256sum "$APK" | tee "$OUTDIR/SHA256.txt"
file "$BUILD/libpm3client.so" | tee "$OUTDIR/native-info.txt"
readelf -d "$BUILD/libpm3client.so" 2>/dev/null | grep NEEDED | tee -a "$OUTDIR/native-info.txt" || true

echo
echo "APK READY: $APK"
