from pathlib import Path
import re

root=Path('.')
reading=(root/'src/org/proxmarkdesk/android/ReadingPage.java').read_text(encoding='utf-8')
main=(root/'src/org/proxmarkdesk/android/MainActivity.java').read_text(encoding='utf-8')
registry=(root/'src/org/proxmarkdesk/android/capability/ActionRegistry.java').read_text(encoding='utf-8')
features=(root/'src/org/proxmarkdesk/android/FeaturePages.java').read_text(encoding='utf-8')
service=(root/'src/org/proxmarkdesk/android/ClientService.java').read_text(encoding='utf-8')

header=reading.index('list.addHeaderView(head,null,false);')
first_return=reading.index('return;', header)
adapter=reading.find('list.setAdapter(', header, first_return)
assert adapter != -1, 'ReadingPage must install an adapter before any early return after addHeaderView'
assert 'String[] names={"Авто","Поиск HF","Поиск LF"}' in reading, 'three quick reading buttons must exist'
assert 'actions={"tag.auto","hf.search","lf.search"}' in reading, 'quick buttons must use logical action ids'

used=set(re.findall(r'actions\.run\("([^"]+)"', reading+main+features))
defined=set(re.findall(r'\.id\("([^"]+)"\)', registry))
missing=sorted(used-defined)
assert not missing, f'Action ids used by UI but absent from registry: {missing}'


primary='String[] pages={"Карта","Операции","Библиотека","Инструменты"}'
assert primary in main, 'primary navigation must expose the four approved primary areas'
assert 'case 15:operationPage.show();break;' in main, 'Operations primary area must route to OperationPage'
operation_path=root/'src/org/proxmarkdesk/android/OperationPage.java'
assert operation_path.is_file(), 'OperationPage.java must exist'
operation=operation_path.read_text(encoding='utf-8')
assert 'ActionRegistry.forCardMenu' in operation, 'Operations UI must be capability-driven through strict card menu projection'
assert 'ActionRegistry.unavailableReason' in operation, 'Operations UI must expose availability reasons'
assert 'a.actions.run(action.id' in operation, 'Operations UI must execute logical action ids through ActionExecutor'
assert 'Сниффер' in operation and 'Ключи' in operation, 'specialized Keys and Sniffer screens must stay reachable from Operations'
assert 'bg.setCornerRadius(dp(10))' in main, 'global buttons must use the rounded dark design system'
assert 'nav.addView(b,new LinearLayout.LayoutParams(0,dp(60),1))' in main, 'primary navigation must fit four equal-width areas at one fixed height'
assert '"Устройство","Чтение","Sniff","Консоль","Дампы"' not in main, 'legacy multi-tab row must not remain primary navigation'
assert 'this::stopCommand' not in main and 'a::stopCommand' not in reading+features+operation, 'soft Stop controls must be removed from UI'
assert '"Стоп"' not in operation, 'Operations screen must not expose a soft Stop button'
assert 'Theme.Material.Light.NoActionBar' not in (root/'AndroidManifest.xml').read_text(encoding='utf-8'), 'app must not use a light system theme over the dark UI'
assert 'Theme.Material.NoActionBar' in (root/'AndroidManifest.xml').read_text(encoding='utf-8'), 'app must use the dark Material theme'
assert 'EditText themedEditText' in main and 'CheckBox themedCheckBox' in main and 'ArrayAdapter<String> darkAdapter' in main, 'dark widget helpers must exist'
assert 'simple_list_item_1' not in reading+features, 'dark screens must not use light system list rows'
assert 'simple_spinner_dropdown_item' not in main, 'spinner must not use the light system dropdown row'
assert 'simple_list_item_1' not in main, 'dark adapter must not delegate row text styling to a system layout'
assert 'new TextView(MainActivity.this)' in main, 'dark adapter must create its own TextView with explicit colors'
assert 'new LinearLayout.LayoutParams(0,dp(60),1)' in main, 'primary navigation tabs must have one fixed equal height'
assert 'b.setGravity(Gravity.CENTER)' in main, 'primary navigation labels must be vertically centered'
assert 'b.setMaxLines(2)' in main, 'primary navigation labels must be bounded to two lines'
assert 'special.setTextColor(a.DISABLED)' in reading, 'disabled catalogue checkbox must keep an explicit readable dark-theme color'
assert 'run("hw version")' not in main and 'run("hw status")' not in main and 'run("hw tune")' not in main, 'predefined diagnostics must use ActionExecutor'
assert 'run("script run pmdesk_envcheck"' not in reading, 'Lua envcheck must use ActionExecutor'
assert 'a.run("lf read"' not in features and 'a.run("lf search -1"' not in features, 'predefined signal actions must use ActionExecutor'
assert 'currentCapability = CardCapability.UNKNOWN;' in service, 'new search must invalidate stale capability before dispatch'
assert 'publishState("Поиск и получение сведений…", null);' in service, 'search invalidation must be published immediately'

bad=[]
for path in root.rglob('*'):
    if path == Path('tests/SourceRegressionTests.py'):
        continue
    if not path.is_file() or path.suffix.lower() not in {'.java','.md','.txt','.tsv','.py','.sh','.xml'}:
        continue
    data=path.read_bytes()
    if data.startswith(b'\xef\xbb\xbf'):
        bad.append(f'BOM:{path}')
        continue
    try:
        text=data.decode('utf-8')
    except UnicodeDecodeError as exc:
        bad.append(f'UTF8:{path}:{exc}')
        continue
    if '\ufffd' in text or 'Ð' in text or 'Ñ' in text or 'Ã' in text:
        bad.append(f'MOJIBAKE:{path}')
assert not bad, 'Encoding regressions: '+', '.join(bad[:20])
print(f'PASS source regression checks; logical actions used={len(used)}, four-area shell present, encoding scan clean')

# Context-action architecture
assert 'ActionRegistry.forCardMenu' in operation, 'Operations must use strict card-specific projection'
assert 'legacyReadPage();' not in reading, 'live card screen must not open the legacy generic read/dump form'
assert 'Формы чтения и сохранения дампа' not in reading, 'legacy generic forms button must be removed'
assert 'Консоль Iceman' in main and '()->page(3)' in main, 'manual Iceman console must be reachable from Tools'
assert 'stopClientProcess();' not in service[service.index('catch (TimeoutException ex)'):service.index('} catch (Exception e)', service.index('catch (TimeoutException ex)'))], 'command timeout must not kill the PM3 client automatically'

legacy_calls=(reading+features+main).count('legacyReadPage();')
assert legacy_calls == 0, f'legacy generic read form must have no UI entry points, found {legacy_calls}'
assert 'Действия для этой карты' in reading and 'Действия для этой карты' in main, 'card and keys flows must route to contextual operations'
assert 'commandStartedAtMs' in operation and 'Выполняется:' in operation, 'Operations must show elapsed command runtime'

# Dictionary-backed protocol actions
assert 'hf mf fchk --{size}' in registry, 'Classic contextual key check must use fast fchk'
assert 'classic.chk.file' in registry and 'desfire.chk.file' in registry and 't55xx.chk.file' in registry, 'dictionary actions must be registered per supported protocol'
assert 'pickDictionary' in operation and 'dictionaryPath' in operation, 'Operations UI must support importing/selecting protocol dictionary files'
assert 'request==33' in main, 'MainActivity must handle dictionary file picker result'

# Embedded PM3 client must not treat queued command-pipe input as a keyboard abort.
pm3_util = (root.resolve().parent/'proxmark3-v4.21611/client/src/util.c').read_text(encoding='utf-8')
assert 'getenv("PMDESK_PIPE")' in pm3_util and 'return 0;' in pm3_util, \
    'Embedded PM3 must ignore keyboard-abort polling when ProxmarkDesk marks stdin as command transport'

# 1.10.4: embedded pipe mode must be explicit (su may present stdin as a TTY/PTY).
rootlaunch=(root/'src/org/proxmarkdesk/android/RootLaunch.java').read_text(encoding='utf-8')
assert 'PMDESK_PIPE' in rootlaunch, 'root client launch must export explicit PMDESK_PIPE marker'
assert 'PMDESK_PIPE' in service, 'direct client launch must set explicit PMDESK_PIPE environment marker'
pm3_util=(root.resolve().parent/'proxmark3-v4.21611/client/src/util.c').read_text(encoding='utf-8')
assert 'getenv("PMDESK_PIPE")' in pm3_util, 'kbd polling must use explicit PMDESK_PIPE marker, not TTY heuristics'

# The Termux builder must not silently fall back to an older prebuilt when the pipe fix is source-only.
builder=(root.resolve().parent/'build-termux.sh').read_text(encoding='utf-8')
assert 'NATIVE_MODE="source"' in builder, '1.10.4 must require a source native build so the pipe fix reaches the APK'

# Operations elapsed time must update while staying on the page.
assert 'operationPage.tick(ss)' in main, 'main 500ms refresh loop must tick the Operations runtime display'
assert 'void tick(SessionState session)' in operation, 'OperationPage must expose a live elapsed-time tick method'
