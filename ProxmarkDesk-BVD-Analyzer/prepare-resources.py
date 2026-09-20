from pathlib import Path
import json,re,hashlib,zipfile,argparse
p=argparse.ArgumentParser();p.add_argument('--source',type=Path,required=True);a=p.parse_args()
root=Path(__file__).resolve().parent;source=a.source.resolve();client=source/'client'
files={f.relative_to(client).as_posix():f.read_bytes() for d in ('lualibs','luascripts','cmdscripts','dictionaries','resources') for f in (client/d).rglob('*') if f.is_file()}
commands=re.findall(r'^#define\s+(CMD_[A-Za-z0-9_]+)\s+(\S+)',(source/'include/pm3_cmd.h').read_text(),re.M)
files['lualibs/pm3_cmd.lua']=('-- Generated from this build\'s include/pm3_cmd.h\nreturn {\n'+''.join(f' {k} = {v},\n' for k,v in commands)+'}\n').encode()
keys=[]
for line in files['dictionaries/mfc_default_keys.dic'].decode().splitlines():
 m=re.match(r'([0-9a-fA-F]{12})',line)
 if m and m[1].lower() not in keys:keys.append(m[1].lower())
files['lualibs/mfc_default_keys.lua']=('return {\n'+''.join(f" '{k}',\n" for k in keys)+'}\n').encode()
files['lualibs/bit.lua']=b'-- ProxmarkDesk: trace_parse needs band/rshift; Iceman supplies bit32.\nassert(bit32, "Iceman bit32 compatibility module unavailable")\nreturn bit32\n'
# Strip comments, retaining string tokens used by require; long documentation blocks are ignored.
def clean(s):
 return re.sub(r'--\[(=*)\[.*?\]\1\]|--[^\n]*', '',s,flags=re.S)
def requires(s):return sorted(set(re.findall(r'\brequire\s*\(?\s*[\"\x27]([A-Za-z0-9_.]+)[\"\x27]',clean(s))))
builtin={'bin','pm3','os','io','string','table','math','coroutine','package','debug','utf8','bit32'}
modules={Path(k).stem:k for k in files if k.startswith('lualibs/') and k.endswith('.lua')}
modules.update({Path(k).stem:k for k in files if k.startswith('luascripts/') and k.endswith('.lua')})
def deps(path,seen=None):
 seen=set() if seen is None else seen
 if path in seen:return set(),set()
 seen.add(path);needed={path};missing=set()
 for module in requires(files[path].decode(errors='replace')):
  if module in builtin or module=='lpeg':continue # optional dkjson accelerator
  if module not in modules:missing.add(module)
  else:
   found,absent=deps(modules[module],seen);needed|=found;missing|=absent
 return needed,missing
translations='''data_hex_crc|Расчёт контрольной суммы HEX-данных
data_mf_accessdecode|Разбор битов доступа MIFARE Classic
data_mf_bin2eml|Преобразование дампа Classic BIN в EML
data_mf_bin2html|Отчёт HTML из дампа Classic BIN
data_mf_eml2bin|Преобразование дампа Classic EML в BIN
data_mf_eml2html|Отчёт HTML из дампа Classic EML
data_mfu_bin2eml|Преобразование дампа Ultralight BIN в EML
hf_mf_autopwn|Автоматизированное восстановление ключей и чтение Classic
hf_mf_dump_luxeo|Чтение карт Luxeo; специальный формат
hf_mf_keycheck|Проверка ключей MIFARE Classic
hf_mf_format|Форматирование MIFARE Classic
hf_mf_magicrevive|Восстановление специальных Magic-карт
hf_mf_gen3_writer|Запись дампов в специальные карты Gen3
hf_mf_mini_dumpdecrypt|Разбор зашифрованного дампа Classic Mini
hf_mf_em_util|Управление эмулятором Classic
hf_mf_sim_hid|Эмуляция HID на основе Classic
hf_mf_tnp3_clone|Копирование специального формата TNP3
hf_mf_tnp3_dump|Чтение специального формата TNP3
hf_mf_tnp3_sim|Эмуляция специального формата TNP3
hf_mf_uid_downgrade|Изменение длины UID специальной карты
hf_mf_uidbruteforce|Перебор UID для специального сценария Classic
hf_mf_uidkeycalc|Расчёт ключей из UID для поддерживаемых схем
hf_mf_uidkeycalc_mizip|Расчёт ключей схемы MiZIP
hf_mf_ultimatecard|Работа со специальной Ultimate-картой
hf_mf_uscuid_prog|Настройка специальной USCUID-карты
hf_mfu_amiibo_restore|Восстановление дампа Amiibo; нужен Python-помощник pyamiibo
hf_mfu_amiibo_sim|Эмуляция данных Amiibo
hf_mfu_magicwrite|Запись специальной Magic Ultralight
hf_mfu_pwdgen_italy|Расчёт пароля для специальной схемы Ultralight
hf_mfu_setuid|Изменение UID совместимой специальной Ultralight
hf_mfu_ultra|Запись и очистка специальных Ultra/UL-5
hf_ndef_dump|Чтение данных NDEF
hf_ntag_3d|Работа с NTAG специального формата 3D-принтера
hf_ntag_bruteforce|Подбор пароля NTAG; возможны ограничения попыток
hf_ntag_dt|Обработка NTAG специального формата DT
hf_14a_aztek|Работа с метками специального формата Aztek
hf_14a_i2crevive|Восстановление NTAG I2C
hf_i2c_plus_2k_utils|Утилиты NTAG I2C Plus 2K
hf_14a_protectimus_nfc|Настройка специальных Protectimus NFC
hf_14a_raw|Обмен необработанными кадрами ISO14443-A
hf_14a_read_ltocm|Чтение меток LTO-CM
hf_14b_calypso|Чтение специальных карт Calypso
hf_14b_mobib|Разбор специального формата MOBIB
hf_15_magic|Работа со специальными Magic ISO15693
hf_mfp_raw|Обмен необработанными кадрами MIFARE Plus
hf_legic|Операции с метками LEGIC
hf_legic_buffer2card|Запись буфера в метку LEGIC
hf_legic_clone|Копирование меток LEGIC
lf_awid_bulkclone|Последовательная запись идентификаторов AWID
lf_electra|Работа с LF-метками ELECTRA
lf_em4100_bulk|Последовательная обработка EM4100
lf_em4x05_kybercrystals|Специальный формат Kyber Crystals на EM4x05
lf_em_tearoff|Прерывание питания EM в заданный момент
lf_em_tearoff_protect|Проверка защиты EM при прерывании питания
lf_hid_bulkclone|Последовательная запись HID
lf_hid_bulkclone_v2|Последовательная запись HID, вариант 2
lf_ident_json|Идентификация LF и сохранение JSON
lf_ioprox_bulkclone|Последовательная запись IOProx
lf_t55xx_chk_date|Проверка даты T55xx
lf_t55xx_chk|Проверка T55xx
lf_t55xx_fix|Восстановление T55xx
lf_t55xx_multiwriter|Последовательная запись T55xx
lf_t55xx_reset|Сброс T55xx
init_rdv4|Инициализация аппаратуры RDV4
mem_readpwd|Чтение паролей из памяти устройства
mem_spiffs_readpwd|Чтение паролей из SPIFFS устройства
mfc_hammerlite|Специальная проверка устойчивости Classic
multi_bruteforce|Универсальный сценарий перебора идентификаторов
ntag_clean|Очистка NTAG
ntag_getsig|Получение подписи NTAG
ntag_hammertime|Специальная проверка NTAG'''
ru=dict(line.split('|',1) for line in translations.splitlines())
special={'hf_mfu_ultra':'Ultra/UL-5','hf_mfu_setuid':'Magic','hf_mfu_magicwrite':'Magic','hf_mf_magicrevive':'Magic','hf_mf_gen3_writer':'Gen3','hf_mf_ultimatecard':'Ultimate','hf_mf_uscuid_prog':'USCUID','hf_mf_uid_downgrade':'Magic','hf_mf_dump_luxeo':'Luxeo','hf_mf_mini_dumpdecrypt':'Mini','hf_15_magic':'Magic','hf_14a_i2crevive':'I2C','hf_i2c_plus_2k_utils':'I2C Plus','hf_14a_read_ltocm':'LTO-CM','hf_14a_protectimus_nfc':'Protectimus','hf_14b_calypso':'Calypso','hf_14b_mobib':'MOBIB'}
for n in ru:
 if 'tnp3' in n:special[n]='TNP3'
 if 'amiibo' in n:special[n]='Amiibo'
 if n in ('hf_ntag_3d','hf_ntag_dt','hf_mfu_pwdgen_italy','hf_mf_uidkeycalc_mizip'):special[n]=n.split('_')[-1]
def family(n):
 for start,group in [('data_mfu','hf mfu'),('data_mf','hf mf'),('mfc_','hf mf'),('hf_mfu','hf mfu'),('hf_ntag','hf mfu'),('ntag_','hf mfu'),('hf_i2c','hf mfu'),('hf_mfp','hf mfp'),('hf_mf_','hf mf'),('hf_14a','hf 14a'),('hf_14b','hf 14b'),('hf_15','hf 15'),('hf_legic','hf legic'),('lf_t55','lf t55xx'),('lf_em4100','lf em 410x'),('lf_em4','lf em 4x05'),('lf_em_','lf em'),('lf_electra','lf em 410x'),('lf_hid','lf hid'),('lf_awid','lf awid'),('lf_ioprox','lf io')]:
  if n.startswith(start):return group
 return ''
entries=[]
for path in sorted(files):
 if not(path.startswith('luascripts/') and path.endswith('.lua')):continue
 n=Path(path).stem;s=files[path].decode(errors='replace');needed,missing=deps(path)
 constraints=[]
 if 'io.popen' in clean(s) or 'os.execute' in clean(s):constraints.append('Внешние команды ОС: совместимость с Android не подтверждена')
 if n=='hf_mfu_amiibo_restore':constraints.append('Отсутствуют pyamiibo и amiibo_change_uid.py')
 if n=='init_rdv4' or 'spiffs' in n:constraints.append('Нужны аппаратные возможности, которых может не быть у Easy')
 if '/tests/' in path:constraints.append('Тест разработчика: не проверен на устройстве')
 entries.append(dict(id=path,name=n+'.lua',kind='Lua',group=family(n),description=ru.get(n,'Тест разработчика: '+n.replace('_',' ')),condition=special.get(n,''),constraints=constraints,requires=sorted(needed),missing=sorted(missing),usage=(re.search(r'usage\s*=\s*\[\[(.*?)\]\]',s,re.S).group(1).strip() if re.search(r'usage\s*=\s*\[\[(.*?)\]\]',s,re.S) else ''),fileOnly=n.startswith('data_'),tested='Зависимости проверены статически; выполнение на метке не проверено'))
# Probe only compiles script files. It must not execute their top-level operations.
probe=['-- ProxmarkDesk read-only environment test','local paths = {}']
for path in sorted(k for k in files if k.endswith('.lua')):probe.append('paths[#paths+1] = '+json.dumps(path))
probe+=['local loaded, missing, syntax = 0, 0, 0','local base = (os.getenv("HOME") or "") .. "/.proxmark3/"','package.path=base.."lualibs/?.lua;"..base.."luascripts/?.lua"','for _, path in ipairs(paths) do local f,err=loadfile(base..path); if not f then syntax=syntax+1; print("SYNTAX ERROR "..path..": "..tostring(err)) end end']
# Libraries are definitions; block all device calls and shell/file writes during requires.
probe+=['local function denied() error("Operation forbidden during environment check") end','if core then for k,v in pairs(core) do if type(v)=="function" then core[k]=denied end end end','os.execute=denied; io.popen=denied','local open=io.open; io.open=function(path,mode) if mode and mode~="r" and mode~="rb" then denied() end return open(path,mode) end','package.preload["pm3"]=function() return {pm3=denied} end']
for mod in sorted(Path(k).stem for k in files if k.startswith('lualibs/') and k.endswith('.lua')):probe.append('do local ok,err=pcall(require,'+json.dumps(mod)+'); if ok then loaded=loaded+1 else missing=missing+1; print("MODULE ERROR '+mod+': "..tostring(err)) end end')
probe+=['print("Loaded modules: "..loaded)','print("Missing modules: "..missing)','print("Syntax errors: "..syntax)','if missing==0 and syntax==0 then print("Lua environment OK") else print("Lua environment FAILED") end']
files['luascripts/pmdesk_envcheck.lua']=('\n'.join(probe)+'\n').encode()
assets=root/'assets';assets.mkdir(exist_ok=True)
with zipfile.ZipFile(assets/'pm3-data.zip','w',zipfile.ZIP_DEFLATED) as z:
 for path,data in sorted(files.items()):z.writestr(path,data)
version_source=(root/'src/org/proxmarkdesk/android/capability/CliCompat.java').read_text(encoding='utf-8')
version_match=re.search(r'CURRENT_VERSION\s*=\s*"(v[0-9.]+)"',version_source)
if not version_match:raise RuntimeError('CliCompat.CURRENT_VERSION not found')
manifest={'client':'Iceman '+version_match[1],'files':{k:hashlib.sha256(v).hexdigest() for k,v in sorted(files.items())},'scripts':entries}
manifest['id']=hashlib.sha256(json.dumps(manifest['files'],sort_keys=True).encode()).hexdigest()
(assets/'resource-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
print('Resource files:',len(files),'Lua libraries:',sum(k.startswith('lualibs/') for k in files),'Lua scripts:',len(entries),'Generated PM3 commands:',len(commands))
print('Missing dependencies:',{e['name']:e['missing'] for e in entries if e['missing']})
