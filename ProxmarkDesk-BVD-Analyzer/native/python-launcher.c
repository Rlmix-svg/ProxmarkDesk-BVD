/* SPDX-License-Identifier: GPL-3.0-or-later */
#include <Python.h>
#include <stdio.h>
/* A separate process permits reliable cancellation without killing the UI. */
int main(int argc, char **argv) {
    if (argc < 2) { fprintf(stderr, "Python bootstrap path is required\n"); return 2; }
    PyConfig config;
    PyConfig_InitPythonConfig(&config);
    config.parse_argv = 0;
    config.buffered_stdio = 0;
    config.write_bytecode = 0;
    PyStatus status = PyConfig_SetBytesArgv(&config, argc - 1, argv + 1);
    if (!PyStatus_Exception(status)) status = PyConfig_SetBytesString(&config, &config.executable, argv[0]);
    if (!PyStatus_Exception(status)) status = PyConfig_SetBytesString(&config, &config.run_filename, argv[1]);
    if (!PyStatus_Exception(status)) status = Py_InitializeFromConfig(&config);
    PyConfig_Clear(&config);
    if (PyStatus_Exception(status)) { Py_ExitStatusException(status); return 1; }
    return Py_RunMain();
}
