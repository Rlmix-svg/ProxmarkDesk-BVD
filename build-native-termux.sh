#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP="$ROOT/ProxmarkDesk-BVD-Analyzer"
PM3="$ROOT/proxmark3-v4.23346"
BUILD="$ROOT/.termux-build/native"
OUT="$ROOT/.termux-build/libpm3client.so"
MODE="${1:-auto}"

mkdir -p "$ROOT/.termux-build"

use_prebuilt() {
  echo "[native] Используется проверенный prebuilt libpm3client.so"
  cp "$ROOT/prebuilt/libpm3client.so" "$OUT"
}

if [[ "$MODE" == "prebuilt" ]]; then
  use_prebuilt
  exit 0
fi

rm -rf "$BUILD"
mkdir -p "$BUILD"

echo "[native] Конфигурация Proxmark3 через Termux clang..."
set +e
cmake -G Ninja -S "$PM3/client" -B "$BUILD" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_COMPILER="$(command -v clang)" \
  -DCMAKE_CXX_COMPILER="$(command -v clang++)" \
  -DCMAKE_EXE_LINKER_FLAGS="-Wl,-z,max-page-size=16384" \
  -DSKIPQT=1 -DSKIPBT=1 -DSKIPPYTHON=1 -DSKIPPTHREAD=1 \
  -DSKIPREADLINE=1 -DSKIPLINENOISE=1 -DSKIPGD=1 \
  -DSKIPJANSSONSYSTEM=1 -DSKIPWHEREAMISYSTEM=1 \
  -DEMBED_BZIP2=ON -DEMBED_LZ4=ON \
  -DZLIB_USE_STATIC_LIBS=ON
cfg=$?
if (( cfg == 0 )); then
  cmake --build "$BUILD" --target bzip2 -j "${PMDESK_JOBS:-4}"
  b1=$?
  (( b1 == 0 )) && cmake --build "$BUILD" --target lz4 -j "${PMDESK_JOBS:-4}"
  b2=$?
  (( b1 == 0 && b2 == 0 )) && cmake --build "$BUILD" -j "${PMDESK_JOBS:-4}"
  b3=$?
else
  b1=1; b2=1; b3=1
fi
set -e

if (( cfg != 0 || b1 != 0 || b2 != 0 || b3 != 0 )) || [[ ! -f "$BUILD/proxmark3" ]]; then
  if [[ "$MODE" == "source" ]]; then
    echo "ERROR: native-сборка из исходников не прошла." >&2
    exit 1
  fi
  echo "[native] Сборка из исходников не прошла; переключаюсь на prebuilt." >&2
  use_prebuilt
  exit 0
fi

cp "$BUILD/proxmark3" "$OUT"
llvm-strip --strip-debug "$OUT" || true

# App namespace reliably provides Android system libs. Termux-private DT_NEEDED entries are unsafe.
allowed='^(libc\.so|libm\.so|libdl\.so|libz\.so|liblog\.so|libc\+\+_shared\.so)$'
mapfile -t needed < <(readelf -d "$OUT" 2>/dev/null | sed -n 's/.*Shared library: \[\(.*\)\]/\1/p')
bad=()
for lib in "${needed[@]}"; do
  [[ "$lib" =~ $allowed ]] || bad+=("$lib")
done
if (( ${#bad[@]} )); then
  if [[ "$MODE" == "source" ]]; then
    printf 'ERROR: native binary требует Termux-private библиотеки: %s\n' "${bad[*]}" >&2
    exit 1
  fi
  printf '[native] Непереносимые зависимости (%s); использую prebuilt.\n' "${bad[*]}" >&2
  use_prebuilt
  exit 0
fi

# If libc++ is dynamic, carry the Termux copy into APK later.
if printf '%s\n' "${needed[@]}" | grep -qx 'libc++_shared.so'; then
  candidate="$PREFIX/lib/libc++_shared.so"
  [[ -f "$candidate" ]] || candidate="$PREFIX/lib/libc++.so"
  if [[ -f "$candidate" ]]; then
    cp "$candidate" "$ROOT/.termux-build/libc++_shared.so"
  else
    if [[ "$MODE" == "source" ]]; then
      echo "ERROR: нужен libc++_shared.so, но он не найден." >&2
      exit 1
    fi
    use_prebuilt
  fi
fi

echo "[native] Готово: $OUT"
