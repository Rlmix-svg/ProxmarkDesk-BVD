"""Host checks of the packaged bootstrap and selected upstream file tools."""
import json, os, pathlib, subprocess, sys, tempfile
root=pathlib.Path(sys.argv[1]).resolve()
bootstrap=root/'python/bootstrap.py'
scripts=root/'python/scripts'
checks=0
def run(name,*args):
    global checks
    p=subprocess.run([sys.executable,'-u',str(bootstrap),str(scripts/name),*map(str,args)],capture_output=True,text=True,encoding='utf-8',timeout=30,env={**os.environ,'PYTHONUTF8':'1'})
    assert p.returncode==0,(name,p.stdout,p.stderr)
    checks+=1
    return p.stdout
with tempfile.TemporaryDirectory(prefix='pm3-python-tools-') as temp:
    t=pathlib.Path(temp)
    raw=bytes(range(256))*4
    (t/'input.mfd').write_bytes(raw)
    run('pm3_mfd2eml.py',t/'input.mfd',t/'out.eml')
    run('pm3_eml2mfd.py',t/'out.eml',t/'roundtrip.mfd')
    assert (t/'roundtrip.mfd').read_bytes()==raw
    checks+=1
    run('python_selftest.py')
    run('parity.py','10','1234')
    run('xorcheck.py','04','00','80','64','ba')
    run('findbits.py','73','0110010101110011')
    run('pm3_nfc2eml.py','-h')
    run('pm3_help2json.py','-h')
    run('pm3_help2list.py','-h')
    os.environ['PMDESK_LIBRARY']=str(t)
    (t/'dumps').mkdir();(t/'dumps/a.bin').write_bytes(raw)
    out=run('dump_inventory.py')
    report=json.loads(next((t/'reports').glob('inventory-*.json')).read_text())
    assert report[0]['bytes']==len(raw)
    checks+=1
print('PASS Python tools:',checks)
