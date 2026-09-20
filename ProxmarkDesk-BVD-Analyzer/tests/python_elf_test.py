"""Validate Android ELF dependencies and 16 KiB load alignment without executing ARM code."""
import pathlib, struct, sys, zipfile
root=pathlib.Path(sys.argv[1])
libs=root/'python-native/arm64-v8a'
names={p.name for p in libs.glob('*.so')}
system={'libc.so','libm.so','libdl.so','liblog.so','libz.so','libandroid.so'}
count=0
for path in sorted(libs.glob('*.so')):
    data=path.read_bytes()
    assert data[:6]==b'\x7fELF\x02\x01',path.name
    assert struct.unpack_from('<H',data,18)[0]==183,('Not AArch64',path.name)
    phoff=struct.unpack_from('<Q',data,32)[0]
    phsize,phcount=struct.unpack_from('<HH',data,54)
    loads=[];dynamic=[]
    for i in range(phcount):
        ptype,flags,offset,address,physical,size,memsize,align=struct.unpack_from('<IIQQQQQQ',data,phoff+i*phsize)
        if ptype==1:
            assert align>=16384,('Non-16KiB library',path.name,align)
            loads.append((address,offset,size))
        if ptype==2:
            for at in range(offset,offset+size,16):
                tag,value=struct.unpack_from('<qQ',data,at)
                if tag==0:break
                dynamic.append((tag,value))
    straddr=next(v for t,v in dynamic if t==5)
    stroffset=next(offset+straddr-address for address,offset,size in loads if address<=straddr<address+size)
    for tag,value in dynamic:
        if tag==1:
            start=stroffset+value
            needed=data[start:data.index(b'\0',start)].decode()
            assert needed in names|system,('Missing dependency',path.name,needed)
    count+=1
with zipfile.ZipFile(root/'assets/python-home.zip') as z:
    archive=set(z.namelist())
    for required in ('lib/python3.14/encodings/__init__.py','lib/python3.14/_android_support.py','lib/python3.14/LICENSE.txt','bootstrap.py','pm3.py','scripts/python_selftest.py'):
        assert required in archive,required
assert 'libpyext__contextvars.so' in names or b'PyInit__contextvars\0' in (libs/'libpython3.14.so').read_bytes()
print('PASS Python Android ELF:',count,'libraries, dependencies, ARM64, 16KiB and bootstrap resources')
