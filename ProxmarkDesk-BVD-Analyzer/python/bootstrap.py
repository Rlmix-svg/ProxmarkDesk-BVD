"""Load APK-packaged extension modules, then run an explicitly selected script."""
import sys
import os
import importlib.machinery
import importlib.util

class NativeExtensions:
    def find_spec(self, fullname, path=None, target=None):
        if "." in fullname:
            return None
        library = os.path.join(os.environ.get("PMDESK_NATIVE", ""), "libpyext_" + fullname + ".so")
        if os.path.isfile(library):
            loader = importlib.machinery.ExtensionFileLoader(fullname, library)
            return importlib.util.spec_from_file_location(fullname, library, loader=loader)
        return None

sys.meta_path.insert(0, NativeExtensions())
sys.dont_write_bytecode = True
import runpy
import builtins
import json
import traceback

def mobile_input(prompt=""):
    from pm3 import _request
    return _request({"action": "input", "prompt": str(prompt)})["value"]

def main():
    if len(sys.argv) < 2:
        raise SystemExit("Не выбран Python-скрипт")
    script = os.path.abspath(sys.argv[1])
    sys.argv = sys.argv[1:]
    # The bridge takes precedence over upstream SWIG pm3.py and user modules.
    sys.path.insert(0, os.path.dirname(script))
    sys.path.insert(0, os.path.dirname(__file__))
    builtins.input = mobile_input
    code = 0
    try:
        runpy.run_path(script, run_name="__main__")
    except SystemExit as error:
        code = error.code if isinstance(error.code, int) else (0 if error.code is None else 1)
        if error.code is not None and not isinstance(error.code, int):
            print(error.code, file=sys.stderr)
    except BaseException:
        traceback.print_exc()
        code = 1
    sys.stdout.flush()
    sys.stderr.flush()
    raise SystemExit(code)

if __name__ == "__main__":
    main()
