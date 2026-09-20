#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Standalone host build. No Python/Termux is needed on the phone."""
import argparse, pathlib, subprocess, zipfile, shutil, os, hashlib
p=argparse.ArgumentParser(description=__doc__)
for arg in ('jdk','android-jar','build-tools','ndk','native-source','cmake','ninja'):
    p.add_argument('--'+arg,required=True,type=pathlib.Path)
p.add_argument('--build-dir',type=pathlib.Path,default=pathlib.Path('build'))
p.add_argument('--skip-native',action='store_true',help='Reuse build-dir/native/proxmark3')
p.add_argument('--python-prefix',type=pathlib.Path,help='Official CPython 3.14.7 Android prefix; omit to reuse packaged python-native binaries')
a=p.parse_args();root=pathlib.Path(__file__).resolve().parent
b=a.build_dir.resolve();b.mkdir(parents=True,exist_ok=True)
win=os.name=='nt';suffix='.exe' if win else ''
def tool(base,name):return str(base.resolve()/(name+suffix))
def run(args):subprocess.run([str(x) for x in args],check=True)
java=tool(a.jdk/'bin','java'); jar=a.android_jar.resolve();bt=a.build_tools.resolve()
source=a.native_source.resolve();native=b/'native';native.mkdir(exist_ok=True)
if not a.skip_native:
    run([a.cmake,'-G','Ninja','-S',source/'client','-B',native,
         '-DCMAKE_TOOLCHAIN_FILE='+str(a.ndk.resolve()/'build/cmake/android.toolchain.cmake'),
         '-DCMAKE_MAKE_PROGRAM='+str(a.ninja.resolve()),'-DANDROID_ABI=arm64-v8a',
         '-DANDROID_PLATFORM=android-30','-DANDROID_STL=c++_static',
         '-DCMAKE_EXE_LINKER_FLAGS=-Wl,-z,max-page-size=16384',
         *['-D'+x+'=1' for x in ('SKIPQT','SKIPBT','SKIPPYTHON','SKIPPTHREAD','SKIPREADLINE','SKIPLINENOISE','SKIPGD','SKIPJANSSONSYSTEM','SKIPWHEREAMISYSTEM')]])
    run([a.cmake,'--build',native,'-j','6'])
assets=root/'assets';assets.mkdir(exist_ok=True)
host='windows-x86_64' if win else ('darwin-x86_64' if __import__('sys').platform=='darwin' else 'linux-x86_64')
if a.python_prefix:
    run([__import__('sys').executable,root/'prepare-python.py','--prefix',a.python_prefix,'--ndk-bin',a.ndk/'toolchains/llvm/prebuilt'/host/'bin','--iceman',source])
if not (root/'python-native/arm64-v8a/libpmdeskpython.so').is_file() or not (assets/'python-home.zip').is_file():
    raise SystemExit('Python runtime missing: provide --python-prefix or the packaged python-native directory')
run([__import__('sys').executable,root/'prepare-resources.py','--source',source])
with zipfile.ZipFile(assets/'licenses.zip','w',zipfile.ZIP_DEFLATED) as z:
    for f in source.rglob('*'):
        if f.is_file() and f.name.lower().startswith(('license','copying','copyright','notice')):
            z.write(f,f.relative_to(source).as_posix())
shutil.copyfile(source/'LICENSE.txt',root/'LICENSE.txt')
classes=b/'classes';classes.mkdir(exist_ok=True)
run([java,root/'CompileApp.java',jar,classes,*sorted((root/'src').rglob('*.java'))])
classesjar=b/'classes.jar'
with zipfile.ZipFile(classesjar,'w') as z:
    for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes).as_posix())
dex=b/'dex';dex.mkdir(exist_ok=True)
run([java,'-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--lib',jar,'--min-api','30','--output',dex,classesjar])
base=b/'unsigned.apk'
run([tool(bt,'aapt2'),'link','-o',base,'-I',jar,'--manifest',root/'AndroidManifest.xml','-A',assets,'--min-sdk-version','30','--target-sdk-version','36'])
host='windows-x86_64' if win else ('darwin-x86_64' if __import__('sys').platform=='darwin' else 'linux-x86_64')
binary=b/'libpm3client.so';shutil.copyfile(native/'proxmark3',binary)
run([tool(a.ndk/'toolchains/llvm/prebuilt'/host/'bin','llvm-strip'),'--strip-debug',binary])
with zipfile.ZipFile(base,'a',zipfile.ZIP_DEFLATED) as z:
    for f in dex.glob('*.dex'):z.write(f,f.name)
    z.write(binary,'lib/arm64-v8a/libpm3client.so')
    for f in sorted((root/'python-native/arm64-v8a').glob('*.so')):z.write(f,'lib/arm64-v8a/'+f.name)
aligned=b/'aligned.apk';run([tool(bt,'zipalign'),'-P','16','-f','4',base,aligned])
key=b/'development.keystore'
if not key.exists():
    run([tool(a.jdk/'bin','keytool'),'-genkeypair','-keystore',key,'-storepass','android','-keypass','android',
         '-alias','androiddebugkey','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=ProxmarkDesk Local Development','-noprompt'])
out=root/'ProxmarkDesk-Android-arm64.apk'
signer=[java,'-jar',bt/'lib/apksigner.jar']
run(signer+['sign','--ks',key,'--ks-pass','pass:android','--key-pass','pass:android','--out',out,aligned])
run(signer+['verify','--verbose',out])
run([tool(bt,'zipalign'),'-c','-P','16','4',out])
(root/'SHA256.txt').write_text(hashlib.sha256(out.read_bytes()).hexdigest()+'  '+out.name+'\n',encoding='utf-8')
print('APK READY:',out)
