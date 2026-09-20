#!/usr/bin/env python3
"""Package an official CPython Android prefix and compile the process launcher.
Inputs are explicit; this script never downloads or changes native Iceman sources.
"""
import argparse, hashlib, json, pathlib, shutil, subprocess, zipfile, os
p = argparse.ArgumentParser()
p.add_argument("--prefix", type=pathlib.Path, required=True)
p.add_argument("--ndk-bin", type=pathlib.Path, required=True)
p.add_argument("--iceman", type=pathlib.Path, required=True)
a = p.parse_args()
root = pathlib.Path(__file__).resolve().parent
native = root / "python-native" / "arm64-v8a"
native.mkdir(parents=True, exist_ok=True)
prefix = a.prefix.resolve()
for name in ("libpython3.14.so", "libcrypto_python.so", "libssl_python.so", "libsqlite3_python.so"):
    shutil.copyfile(prefix / "lib" / name, native / name)
for module in (prefix / "lib/python3.14/lib-dynload").glob("*.so"):
    if module.name.startswith("_test") or module.name.startswith("_xxtest") or module.name.startswith("xx"):
        continue
    shutil.copyfile(module, native / ("libpyext_" + module.name.split(".")[0] + ".so"))
compiler = a.ndk_bin / ("clang.exe" if os.name == "nt" else "clang")
subprocess.run([str(compiler), "--target=aarch64-linux-android30", "-O2", "-fPIE", "-pie",
    "-Wl,-z,max-page-size=16384", "-Wl,-rpath,$ORIGIN", "-I" + str(prefix / "include/python3.14"),
    str(root / "native/python-launcher.c"), "-L" + str(prefix / "lib"), "-lpython3.14",
    "-o", str(native / "libpmdeskpython.so")], check=True)
utilities = ("pm3_eml2mfd.py", "pm3_mfd2eml.py", "pm3_nfc2eml.py", "findbits.py", "parity.py",
             "xorcheck.py", "pm3_help2json.py", "pm3_help2list.py")
for name in utilities:
    shutil.copyfile(a.iceman / "client/pyscripts" / name, root / "python/scripts" / name)
# Android has no Git checkout: enumerate bundled script files directly.
helper = root / "python/scripts/pm3_help2list.py"
text = helper.read_text(encoding="utf-8")
start = text.index("    # Collect all git-tracked scripts")
end = text.index("    args.output_file.write", start)
text = text[:start] + '''    # ProxmarkDesk Android adaptation: no external git process is required.
    paths = [('*.py', Path(__file__).parent),
             ('*.lua', Path.home() / '.proxmark3/luascripts'),
             ('*.cmd', Path.home() / '.proxmark3/cmdscripts')]
    for pattern, scripts_dir in paths:
        for file in scripts_dir.glob(pattern):
            command_data[file.name] = {'command': f'script run {file.name}', 'offline': False}

''' + text[end:]
helper.write_text(text, encoding="utf-8")
with zipfile.ZipFile(root / "assets/python-home.zip", "w", zipfile.ZIP_DEFLATED) as z:
    for f in sorted((prefix / "lib/python3.14").rglob("*")):
        rel = f.relative_to(prefix)
        if not f.is_file() or any(x in rel.parts for x in ("__pycache__", "test", "tests", "idlelib", "tkinter", "ensurepip", "lib-dynload", "site-packages", "config-3.14-aarch64-linux-android")):
            continue
        if f.suffix not in (".pyc", ".so", ".a"):
            z.write(f, rel.as_posix())
    for f in sorted((root / "python").rglob("*.py")):
        z.write(f, f.relative_to(root / "python").as_posix())
manifest = {"version": "3.14.7", "source": "https://www.python.org/ftp/python/3.14.7/python-3.14.7-aarch64-linux-android.tar.gz",
            "archive_sha256": "6d50cc3aa66e414a439594089bcdfb5f1264358155c70c1f00471c24cfb477fb",
            "native": {f.name: hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(native.glob("*.so"))},
            "assets_sha256": hashlib.sha256((root / "assets/python-home.zip").read_bytes()).hexdigest()}
(root / "assets/python-runtime.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
print("Packaged Python:", len(manifest["native"]), "native libraries")
