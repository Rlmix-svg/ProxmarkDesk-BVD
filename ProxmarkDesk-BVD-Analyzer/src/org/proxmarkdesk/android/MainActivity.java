// SPDX-License-Identifier: GPL-3.0-or-later
package org.proxmarkdesk.android;
import org.proxmarkdesk.android.capability.CliCompat;
import java.lang.Process;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.usb.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public class MainActivity extends Activity {
    private String appVersion(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(PackageManager.NameNotFoundException e){return "не определена";}}
    final FirmwarePage firmwarePage=new FirmwarePage(this);
    final ReadingPage readingPage=new ReadingPage(this); final FeaturePages features=new FeaturePages(this); final PythonPage pythonPage=new PythonPage(this); final OperationPage operationPage=new OperationPage(this); final ActionExecutor actions=new ActionExecutor(this); long shownPythonPrompt=-1; File exportSource; String displayedConsole="";
    byte[] bvdTrace; boolean bvdTraceBinary; String bvdReport="";
    File profilesDir(){return new File(service==null?new File(getFilesDir(),"library"):service.library,"bvd-profiles");}
    void bvdPage(){LinearLayout box=content();box.addView(text("BVD Analyzer · профили NTAG",21));
        box.addView(button("Создать профиль из сохранённого дампа",this::chooseBvdDump));
        box.addView(button("Импорт дампа",()->pick(6)));
        box.addView(button("Сравнить сохранённые профили",()->{try{List<JSONObject> p=new ArrayList<>();File[] fs=profilesDir().listFiles((d,n)->n.endsWith(".json"));if(fs!=null)for(File f:fs)p.add(BvdProfiles.read(f));bvdReport=BvdProfiles.compare(p);showBvdReport();}catch(Exception e){message(e.getMessage());}}));
        box.addView(button("Импорт трассы A (.trace / hf 14a list)",()->pick(7)));
        box.addView(button("Сравнить A с трассой B",()->{if(bvdTrace==null)message("Сначала импортируйте трассу A");else pick(8);}));
        box.addView(button("Сохранить текущую HF-трассу с Proxmark3",()->{Map<String,String> p=new HashMap<>();p.put("path",dumpName("bvd-trace"));actions.run("trace.hf14a.save",p,120);}));
        box.addView(text("Создавайте отдельный профиль каждого чтения. Отметка «Рабочая / Не открывает» — результат вашей проверки на считывателе.",14));
        File[] fs=profilesDir().listFiles((d,n)->n.endsWith(".json"));if(fs!=null){Arrays.sort(fs,(a,b)->Long.compare(b.lastModified(),a.lastModified()));for(File f:fs)try{JSONObject p=BvdProfiles.read(f);box.addView(button(p.optString("name")+" · "+p.optString("uid")+" · "+p.optString("outcome"),()->new AlertDialog.Builder(this).setTitle(p.optString("name")).setItems(new String[]{"Отчёт","Экспорт JSON","Удалить профиль"},(d,n)->{try{if(n==0){bvdReport=BvdProfiles.report(p);showBvdReport();}else if(n==1)export(p.toString(2).getBytes(StandardCharsets.UTF_8),f.getName());else new AlertDialog.Builder(this).setMessage("Удалить этот профиль?").setPositiveButton("Удалить",(x,y)->{if(f.delete())page(9);else message("Не удалось удалить профиль");}).setNegativeButton("Отмена",null).show();}catch(Exception e){message(e.getMessage());}}).show()));}catch(Exception e){box.addView(text("Ошибка профиля "+f.getName()+": "+e.getMessage(),12));}}
    }
    void showBvdReport(){body.removeAllViews();LinearLayout box=content();box.addView(button("Назад к профилям",()->page(9)));box.addView(button("Экспорт отчёта",()->export(bvdReport.getBytes(StandardCharsets.UTF_8),"bvd-report.txt")));TextView t=text(bvdReport,13);t.setTypeface(Typeface.MONOSPACE);box.addView(t);}
    void chooseBvdDump(){if(!ready())return;File[] fs=service.dumps.listFiles((d,n)->n.toLowerCase(Locale.ROOT).matches(".*\\.(json|bin|eml)$"));if(fs==null||fs.length==0){message("Сначала сохраните или импортируйте дамп");return;}String[] names=new String[fs.length];for(int i=0;i<fs.length;i++)names[i]=fs[i].getName();new AlertDialog.Builder(this).setTitle("Дамп NTAG").setItems(names,(d,i)->editBvdProfile(fs[i])).show();}
    void editBvdProfile(File file){LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(14),0,dp(14),0);EditText name=input(box,"Имя профиля",file.getName());Spinner outcome=spinner(box,"Результат на считывателе",new String[]{"Не проверена","Рабочая","Не открывает"});EditText pwd=input(box,"Известный PWD (необязательно)","");EditText pack=input(box,"PACK (необязательно)","");new AlertDialog.Builder(this).setTitle("Профиль из дампа").setView(box).setPositiveButton("Сохранить",(d,n)->{try{JSONObject p=BvdProfiles.create(file,name.getText().toString(),outcome.getSelectedItem().toString(),pwd.getText().toString().trim(),pack.getText().toString().trim());BvdProfiles.save(profilesDir(),p);page(9);}catch(Exception e){message(e.getMessage());}}).setNegativeButton("Отмена",null).show();}
    final int INK=Color.rgb(12,28,43), TEAL=Color.rgb(0,200,170), BG=Color.rgb(8,20,31), TEXT=Color.rgb(225,236,243), MUTED=Color.rgb(150,174,188), DISABLED=Color.rgb(113,136,149);
    ClientService service; boolean bound;
    LinearLayout root,body,nav; TextView status,console,fileText,analysisText; ScrollView consoleScroll;
    EditText port,command,password,keyPath,block,authUid; Spinner family,cardSize,keyType,sniffMode,layout;
    android.widget.ProgressBar progressBar; TextView progressText;
    String savedUid="",savedPassword="",pendingCommand=""; long pendingRevision; boolean autoConnecting; int connectGeneration;
    final BroadcastReceiver usbEvents=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        UsbDevice device=i.getParcelableExtra(UsbManager.EXTRA_DEVICE);if(device==null)return;
        boolean proxmark=(device.getVendorId()==0x9ac4&&device.getProductId()==0x4b8f)||(device.getProductName()!=null&&device.getProductName().toLowerCase(Locale.ROOT).contains("proxmark"));
        if(!proxmark)return;
        if(UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(i.getAction())){
            if(service!=null)service.onUsbAttached();
            autoConnect(true);
        } else {
            ++connectGeneration;autoConnecting=false;
            if(service!=null)service.onUsbDetached();
        }
    }};
    CheckBox rootMode; int currentPage; long lastRevision=-1; Handler timer=new Handler();
    File selectedFile; byte[] selectedBytes,exportBytes; String exportName; int fileAction; String pendingDictionaryAction="";
    ArrayList<String> commands=new ArrayList<>();
    boolean discovering;
    TextView tagText; long shownTagRevision=-1;
    final String[] familyNames=TagInfo.FAMILIES;
    final String[] infos=TagInfo.INFOS;
    final String[] sniffs={"hf 14a sniff","hf 14b sniff","hf 15 sniff","hf felica sniff","hf iclass sniff","hf topaz sniff","lf sniff"};
    final String[] traces={"hf 14a list","hf 14b list","hf 15 list","hf felica list","hf iclass list","hf topaz list","lf search -1"};
    final String[] sniffActionIds={"hf.sniff.14a","sniff.hf14b","sniff.hf15","sniff.felica","sniff.iclass","sniff.topaz","sniff.lf"};
    final String[] traceActionIds={"trace.hf14a","trace.hf14b","trace.hf15","trace.felica","trace.iclass","trace.topaz","trace.lf"};
    final ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){service=((ClientService.LocalBinder)b).get();bound=true;page(currentPage);autoConnect();}public void onServiceDisconnected(ComponentName n){bound=false;service=null;}};
    final Runnable refresh=new Runnable(){public void run(){if(service!=null){
        org.proxmarkdesk.android.session.SessionState ss=service.sessionState;
        if(ss==null)ss=org.proxmarkdesk.android.session.SessionState.INITIAL;

        // Python input dialog
        if(service.pythonPrompt!=null&&shownPythonPrompt!=service.pythonPromptRevision){shownPythonPrompt=service.pythonPromptRevision;final ClientService target=service;final long promptId=shownPythonPrompt;EditText answer=themedEditText();new AlertDialog.Builder(MainActivity.this).setTitle("Ввод для Python").setMessage(service.pythonPrompt).setView(answer).setPositiveButton("Отправить",(d,w)->{if(target.pythonPromptRevision==promptId)target.answerPython(answer.getText().toString());}).setNegativeButton("Отмена",(d,w)->{if(target.pythonPromptRevision==promptId)target.answerPython(null);}).setOnCancelListener(d->{if(target.pythonPromptRevision==promptId)target.answerPython(null);}).show();}

        // Status indicator (uses SessionState)
        boolean online = ss.client == org.proxmarkdesk.android.session.ClientState.READY;
        boolean offline= ss.client == org.proxmarkdesk.android.session.ClientState.OFFLINE;
        String statusLabel = ss.statusText.isEmpty() ? (online?"Онлайн":offline?"Офлайн · без устройства":"Офлайн") : ss.statusText;
        SpannableString indicator=new SpannableString("● "+statusLabel);
        indicator.setSpan(new android.text.style.ForegroundColorSpan(online?Color.rgb(0,160,75):Color.RED),0,1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        status.setText(indicator);

        // Console append
        if(console!=null&&lastRevision!=ss.revision){String next=service.console();if(next.startsWith(displayedConsole))console.append(coloredConsole(next.substring(displayedConsole.length())));else console.setText(coloredConsole(next));displayedConsole=next;lastRevision=ss.revision;}

        // Command completion: clear draft field
        if(!pendingCommand.isEmpty()&&!ss.isBusy()&&ss.currentCommand.equals(pendingCommand)){
            if(prefs().getString("draft","").equals(pendingCommand))prefs().edit().putString("draft","").apply();
            if(command!=null&&command.getText().toString().equals(pendingCommand))command.setText("");pendingCommand="";
        }

        // Progress bar visibility
        if(progressBar!=null){
            org.proxmarkdesk.android.session.ProgressState ps=ss.progress;
            if(ps!=null&&ss.isBusy()){progressBar.setVisibility(android.view.View.VISIBLE);progressBar.setMax(ps.total>0?ps.total:100);progressBar.setProgress(ps.current);if(progressText!=null)progressText.setText(ps.summary());}
            else{progressBar.setVisibility(android.view.View.GONE);if(progressText!=null)progressText.setText("");}
        }

        // Live Operations runtime; do not require a page rebuild just to advance the clock.
        if(currentPage==15)operationPage.tick(ss);

        // Tag card refresh
        if(currentPage==1&&shownTagRevision!=ss.revision)refreshTag();
    }timer.postDelayed(this,500);}};
    CharSequence coloredConsole(String value){SpannableString result=new SpannableString(value);for(int[] range:UiLogic.commandRanges(value))result.setSpan(new android.text.style.ForegroundColorSpan(Color.rgb(70,220,100)),range[0],range[1],Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);return result;}
    @Override public void onCreate(Bundle state){super.onCreate(state);root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(BG);setContentView(root);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(0);
        root.setOnApplyWindowInsetsListener((v,insets)->{Insets x=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(x.left,x.top,x.right,x.bottom);return insets;});
        TextView title=text("ProxmarkDesk BVD "+appVersion(),24);title.setTextColor(Color.WHITE);title.setBackgroundColor(INK);title.setPadding(dp(16),dp(12),dp(16),dp(12));root.addView(title);
        status=text("Самостоятельный клиент Iceman",12);status.setPadding(dp(12),dp(6),dp(12),dp(6));root.addView(status);
        nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);nav.setPadding(dp(6),dp(4),dp(6),dp(4));String[] pages={"Карта","Операции","Библиотека","Инструменты"};int[] primaryPages={1,15,4,14};for(int i=0;i<pages.length;i++){final int p=primaryPages[i];Button b=button(pages[i],()->page(p));b.setGravity(Gravity.CENTER);b.setMaxLines(2);b.setTextSize(13);nav.addView(b,new LinearLayout.LayoutParams(0,dp(60),1));}root.addView(nav);
        body=new LinearLayout(this);body.setOrientation(1);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        bindService(new Intent(this,ClientService.class),connection,BIND_AUTO_CREATE);timer.post(refresh);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1);
        IntentFilter usbFilter=new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED);usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);registerReceiver(usbEvents,usbFilter,Context.RECEIVER_NOT_EXPORTED);
        page(1);
    }
    int dp(int x){return (int)(getResources().getDisplayMetrics().density*x+.5f);}
    TextView text(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(TEXT);v.setTextIsSelectable(true);return v;}
    Button button(String s,Runnable action){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(TEXT);b.setTextSize(14);b.setMinHeight(dp(48));b.setPadding(dp(10),dp(8),dp(10),dp(8));android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(Color.rgb(13,34,49));bg.setCornerRadius(dp(10));bg.setStroke(dp(1),Color.rgb(36,91,108));b.setBackground(bg);b.setOnClickListener(v->guard(action));return b;}
    void guard(Runnable a){try{a.run();}catch(Exception ex){message(ex.getMessage());}}
    void message(String s){new AlertDialog.Builder(this).setTitle("ProxmarkDesk").setMessage(s).setPositiveButton("OK",null).show();}
    LinearLayout content(){ScrollView scroll=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(12),dp(8),dp(12),dp(16));scroll.addView(box);body.addView(scroll,new LinearLayout.LayoutParams(-1,-1));return box;}
    EditText themedEditText(){EditText e=new EditText(this);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setBackgroundTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_enabled},new int[]{}},new int[]{TEAL,DISABLED}));return e;}
    CheckBox themedCheckBox(){CheckBox c=new CheckBox(this);c.setTextColor(TEXT);c.setAlpha(1f);c.setButtonTintList(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{android.R.attr.state_checked},new int[]{-android.R.attr.state_checked}},new int[]{DISABLED,TEAL,MUTED}));return c;}
    ArrayAdapter<String> darkAdapter(List<String> values){return new ArrayAdapter<String>(this,0,values){private TextView row(int p,View convert){TextView t=convert instanceof TextView?(TextView)convert:new TextView(MainActivity.this);t.setText(getItem(p));t.setTextColor(TEXT);t.setTextSize(15);t.setGravity(Gravity.CENTER_VERTICAL);t.setBackgroundColor(BG);t.setPadding(dp(12),dp(10),dp(12),dp(10));t.setMinHeight(dp(48));t.setAlpha(1f);return t;}@Override public View getView(int p,View c,android.view.ViewGroup parent){return row(p,c);}@Override public View getDropDownView(int p,View c,android.view.ViewGroup parent){return row(p,c);}};}
    EditText input(LinearLayout box,String label,String value){box.addView(text(label,13));EditText e=themedEditText();e.setSingleLine();e.setText(value);box.addView(e);return e;}
    Spinner spinner(LinearLayout box,String label,String[] values){box.addView(text(label,13));Spinner s=new Spinner(this);s.setAdapter(darkAdapter(new ArrayList<>(Arrays.asList(values))));s.setPopupBackgroundDrawable(new android.graphics.drawable.ColorDrawable(INK));box.addView(s,new LinearLayout.LayoutParams(-1,dp(48)));return s;}
    void buttons(LinearLayout box,String[] labels,Runnable[] actions){for(int i=0;i<labels.length;i++)box.addView(button(labels[i],actions[i]));}
    boolean ready(){if(service==null){message("Служба запускается, повторите через секунду");return false;}return true;}
    void page(int n){if(currentPage==1&&authUid!=null&&password!=null){rememberPassword(password.getText().toString());savedUid=authUid.getText().toString();savedPassword=password.getText().toString();}currentPage=n;console=null;tagText=null;body.removeAllViews();switch(n){case 0:devicePage();break;case 1:readPage();break;case 2:sniffPage();break;case 3:consolePage();break;case 4:filesPage();break;case 5:journalPage();break;case 6:cataloguePage();break;case 7:emulationPage();break;case 9:bvdPage();break;case 10:features.signals();break;case 11:features.scripts();break;case 12:pythonPage.show();break;case 13:keysPage();break;case 14:toolsPage();break;case 15:operationPage.show();break;default:aboutPage();}}
    android.content.SharedPreferences prefs(){return getSharedPreferences("settings",MODE_PRIVATE);}
    void devicePage(){LinearLayout box=content();box.addView(text("Proxmark3 Easy · USB OTG",21));box.addView(text("Клиент встроен в APK. Termux и отдельная установка Python не требуются.",14));
        port=input(box,"Последовательное устройство",prefs().getString("port","/dev/ttyACM0"));rootMode=themedCheckBox();rootMode.setText("Использовать root для доступа к USB");rootMode.setChecked(prefs().getBoolean("root",true));box.addView(rootMode);
        buttons(box,new String[]{"Найти USB / tty-порты","Офлайн: без устройства","Остановить сеанс","Экспорт диагностики USB"},new Runnable[]{this::discover,()->connect(true),this::stopSession,()->{try{export(Files.readAllBytes(diagnosticFile().toPath()),"proxmark-usb-diagnostics.txt");}catch(IOException e){message("Сначала нажмите «Найти USB / tty-порты»");}}});
        box.addView(button("Обновить устройство",firmwarePage::open));
        box.addView(text("Офлайн запускает только клиент: hw version показывает его сборку. Подключение происходит автоматически при запуске приложения и подключении USB. После остановки сеанса переподключите USB.",14));
        box.addView(text("При первом подключении разрешите root в своём менеджере. Перед подключением закройте другой клиент Proxmark3. Обычное подключение не изменяет прошивку. Для обновления используйте отдельную кнопку.",14));
        box.addView(text("Встроенная версия: Iceman "+CliCompat.CURRENT_VERSION+" · ARM64\nAndroid 16 · Java-интерфейс и собственная библиотека файлов.\nLua и отдельная среда CPython 3.14.7 включены. Python запускается во вкладке Python; нативный SWIG Python, Qt и native Bluetooth не включены.",14));
        features.storage(box);box.addView(button("Лицензии и инструкция",()->{try{message(new String(readStream(getAssets().open("about.txt"),100000),StandardCharsets.UTF_8));}catch(Exception ex){message(ex.toString());}}));
    }
    void stopSession(){if(service!=null&&service.firmwareUpdating.get()){message("Дождитесь завершения обновления");return;}readingPage.manual="";
        ++connectGeneration;autoConnecting=false;if(currentPage==1&&password!=null)rememberPassword(password.getText().toString());
        if(service!=null)service.stopSession();savedUid="";savedPassword="";pendingCommand="";
        if(password!=null)password.setText("");if(authUid!=null)authUid.setText("");if(command!=null)command.setText("");
        prefs().edit().putString("draft","").putInt("family",0).putInt("cardSize",0).apply();
        if(currentPage==1)page(1);if(console!=null)console.setText("");displayedConsole="";
    }
    void autoConnect(){autoConnect(false);}
    void autoConnect(boolean resume){
        if(service==null||autoConnecting||service.firmwareUpdating.get())return;
        org.proxmarkdesk.android.session.SessionState ss=service.sessionState;
        if(ss==null)return;
        if(ss.isBusy()||ss.isClientAlive())return;
        if(!resume&&ss.client==org.proxmarkdesk.android.session.ClientState.STOPPED)return;
        autoConnecting=true;final int generation=++connectGeneration;service.publishState("Поиск Proxmark3 и проверка root…",null);
        final ClientService target=service;final boolean useRoot=prefs().getBoolean("root",true);
        new Thread(()->{UsbDiagnostics.Result result;
            try{result=UsbDiagnostics.run(useRoot,getSystemService(UsbManager.class).getDeviceList().size(),"Автоподключение");}
            catch(Exception e){runOnUiThread(()->{if(generation!=connectGeneration)return;autoConnecting=false;target.publishState("Ошибка подключения: "+e.getMessage(),null);});return;}
            final UsbDiagnostics.Result found=result;
            try{diagnosticFile().getParentFile().mkdirs();Files.write(diagnosticFile().toPath(),found.report.getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}
            runOnUiThread(()->{if(generation!=connectGeneration)return;autoConnecting=false;
                org.proxmarkdesk.android.session.SessionState cur=service==null?null:service.sessionState;
                if(isFinishing()||isDestroyed()||target!=service||target.firmwareUpdating.get()||cur==null||cur.isBusy()||cur.isClientAlive())return;
                String preferred=prefs().getString("port","/dev/ttyACM0");
                String selected=found.ports.size()==1?found.ports.get(0):found.ports.contains(preferred)?preferred:"";
                if(!found.complete||(useRoot&&!found.root)||selected.isEmpty()){target.publishState("Ошибка подключения: "+found.summary,null);return;}
                prefs().edit().putString("port",selected).apply();if(port!=null)port.setText(selected);
                try{startForegroundService(new Intent(this,ClientService.class));target.connect(selected,useRoot,false);}catch(Exception e){target.publishState("Ошибка подключения: "+e.getMessage(),null);}
            });
        },"auto-connect").start();
    }
    String passwordHistoryKey(){String p=service==null?prefs().getString("deviceProfile","Proxmark3-Easy"):service.deviceProfile;return p.equals("Proxmark3-Easy")?"passwordHistory":"passwordHistory:"+p;} void rememberPassword(String value){try{String old=prefs().getString(passwordHistoryKey(),"[]"),uid=authUid==null?"":authUid.getText().toString();String next=UiLogic.history(old,value,uid,System.currentTimeMillis());if(!next.equals(old)){prefs().edit().putString(passwordHistoryKey(),next).apply();if(service!=null){ResultCache.atomic(new File(service.library,"passwords/history.json"),next);service.requestMirror();}}}catch(Exception e){message(e.getMessage());}}
    void passwordHistory(){try{
        JSONArray list=new JSONArray(prefs().getString(passwordHistoryKey(),"[]"));String[] labels=new String[list.length()];
        for(int i=0;i<labels.length;i++){JSONObject item=list.getJSONObject(i);labels[i]=item.optString("value")+" · UID "+item.optString("uid")+" · "+new java.text.SimpleDateFormat("dd.MM HH:mm",Locale.getDefault()).format(new Date(item.optLong("time")));}
        new AlertDialog.Builder(this).setTitle("История паролей").setItems(labels,(d,i)->guard(()->{JSONObject item=list.optJSONObject(i);String uid=item.optString("uid");if(!uid.isEmpty()&&!uid.equals(RfidAuth.clean(authUid.getText().toString())))throw new IllegalArgumentException("Пароль сохранён для UID "+uid+". Сначала найдите эту метку.");password.setText(item.optString("value"));})).setNegativeButton("Закрыть",null).setNeutralButton("Очистить историю",(d,w)->prefs().edit().remove(passwordHistoryKey()).apply()).show();
    }catch(Exception e){message(e.getMessage());}}
    void autoSniff(){TagInfo tag=service==null?null:service.tagInfo;if(tag==null||!tag.detected||tag.ambiguous){message("Сначала выполните поиск метки. Во время sniff активный поиск не запускается.");return;}int mode=sniffForFamily(tag.family);if(mode<0){message("Для этого семейства нет однозначного режима sniff. Выберите протокол вручную.");return;}sniffMode.setSelection(mode);prefs().edit().putInt("sniff",mode).apply();}
    static int sniffForFamily(int family){return UiLogic.sniffForFamily(family);}
    void connect(boolean off){if(!ready())return;++connectGeneration;autoConnecting=false;String p=port==null?prefs().getString("port","/dev/ttyACM0"):port.getText().toString().trim();boolean r=rootMode==null?prefs().getBoolean("root",true):rootMode.isChecked();prefs().edit().putString("port",p).putBoolean("root",r).apply();startForegroundService(new Intent(this,ClientService.class));service.connect(p,r,off);page(3);}
    File diagnosticFile(){return new File(getFilesDir(),"library/usb-diagnostics.txt");}
    void discover(){
        if(discovering){message("Диагностика уже выполняется");return;}
        final boolean useRoot=rootMode!=null&&rootMode.isChecked();
        prefs().edit().putBoolean("root",useRoot).apply();
        int apiCount;StringBuilder info=new StringBuilder();
        try{UsbManager usb=getSystemService(UsbManager.class);Map<String,UsbDevice> devices=usb.getDeviceList();apiCount=devices.size();
            for(UsbDevice d:devices.values())info.append(String.format(Locale.US,"USB %04X:%04X — %s; interfaces=%d; permission=%s\n",d.getVendorId(),d.getProductId(),d.getProductName(),d.getInterfaceCount(),usb.hasPermission(d)));
        }catch(Exception e){apiCount=-1;info.append(e.toString());}
        final int count=apiCount;final String details=info.toString();
        discovering=true;
        Toast.makeText(this,"Проверка USB и root… до 30 секунд",Toast.LENGTH_LONG).show();
        new Thread(()->{
            UsbDiagnostics.Result result;
            try{result=UsbDiagnostics.run(useRoot,count,details);}
            catch(Exception e){result=UsbDiagnostics.parse(new UsbDiagnostics.Capture(-1,false,e.toString()),useRoot,count,details);}
            final UsbDiagnostics.Result found=result;
            String saveError="";
            try{diagnosticFile().getParentFile().mkdirs();Files.write(diagnosticFile().toPath(),found.report.getBytes(StandardCharsets.UTF_8));}
            catch(IOException e){saveError="\nНе удалось сохранить отчёт: "+e.getMessage();}
            final String saveWarning=saveError;
            runOnUiThread(()->{
                discovering=false;if(isFinishing()||isDestroyed())return;
                if(service!=null)service.emit("[Диагностика USB] "+found.summary);
                boolean selectable=found.complete&&(!useRoot||found.root);
                if(selectable&&found.ports.size()==1)selectPort(found.ports.get(0));
                new AlertDialog.Builder(this).setTitle("Диагностика USB")
                    .setMessage(found.summary+saveWarning+"\n\nПолный отчёт можно экспортировать и прислать для проверки.")
                    .setPositiveButton("OK",(d,w)->{if(selectable&&found.ports.size()>1)new AlertDialog.Builder(this).setTitle("Выберите tty-порт").setItems(found.ports.toArray(new String[0]),(dialog,index)->selectPort(found.ports.get(index))).show();})
                    .setNeutralButton("Экспорт",(d,w)->export(found.report.getBytes(StandardCharsets.UTF_8),"proxmark-usb-diagnostics.txt"))
                    .setNegativeButton("Отчёт",(d,w)->message(found.report)).show();
            });
        },"usb-discovery").start();
    }
    void selectPort(String value){prefs().edit().putString("port",value).apply();if(port!=null)port.setText(value);autoConnect(true);}
    void run(String cmd){run(cmd,120);}
    void run(String cmd,int timeout){if(!ready())return;if(!service.sessionState.isClientAlive()){message("Откройте «Устройство» и подключите Proxmark3.");return;}service.run(cmd,timeout);if(AutoInspect.isSearch(cmd)){page(1);}else page(3);}
    void readPage(){family=null;authUid=null;password=null;readingPage.show(false);} void legacyReadPage(){LinearLayout box=content();box.addView(text("Чтение собственной метки",21));
        tagText=text("Выполните поиск: семейство и сведения будут заполнены автоматически.",14);tagText.setPadding(dp(8),dp(10),dp(8),dp(10));box.addView(tagText);
        box.addView(button("Все сведения о найденной метке",()->{if(service!=null&&service.tagInfo!=null)message(service.tagInfo.report());else message("Сначала выполните поиск метки");}));
        box.addView(button("Экспорт карточки метки",()->{if(service!=null&&service.tagInfo!=null)export(service.tagInfo.report().getBytes(StandardCharsets.UTF_8),"tag-info.txt");else message("Сначала выполните поиск метки");}));
        buttons(box,new String[]{"Автопоиск HF / LF","Поиск HF","Поиск LF","Антенны"},new Runnable[]{()->actions.run("tag.auto",300),()->actions.run("hf.search",300),()->actions.run("lf.search",300),()->actions.run("hw.tune",120)});family=spinner(box,"Семейство",familyNames);family.setSelection(prefs().getInt("family",0));family.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?>p){}public void onItemSelected(android.widget.AdapterView<?>p,View v,int i,long id){prefs().edit().putInt("family",i).apply();}});
        box.addView(button("Информация",()->{String info=infos[family.getSelectedItemPosition()];if(info.isEmpty())message("Для этого семейства доступные сведения уже приведены в результате поиска. Дополнительные команды есть в каталоге.");else run(info);}));
        cardSize=spinner(box,"Размер Classic",new String[]{"1k","mini","2k","4k"});cardSize.setSelection(prefs().getInt("cardSize",0));block=input(box,"Номер блока / страницы","0");block.setInputType(2);password=input(box,"Известный пароль / ключ HEX (можно оставить пустым)","");password.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);keyType=spinner(box,"Тип ключа Classic",new String[]{"A","B"});
        authUid=input(box,"UID для проверки PWD_AUTH",savedUid);password.setText(savedPassword);
        final EditText entry=password;final Runnable saveEntry=()->{if(password==entry)rememberPassword(entry.getText().toString());};
        entry.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){timer.removeCallbacks(saveEntry);timer.postDelayed(saveEntry,800);}public void afterTextChanged(Editable e){}});
        authUid.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){password.setText("");savedPassword="";}public void afterTextChanged(Editable e){}});
        box.addView(button("История паролей",this::passwordHistory));
        box.addView(button("Проверить пароль",()->{if(family.getSelectedItemPosition()!=2)throw new IllegalArgumentException("Выберите Ultralight / NTAG с 4-байтовым паролем");if(ready()){service.verifyPassword(authUid.getText().toString(),key(8));page(3);}}));
        box.addView(text("PWD_AUTH: одна попытка, UID сверяется до отправки пароля. PACK проверяется по CRC_A. При смене UID поле ключа очищается.",13));
        buttons(box,new String[]{"Прочитать блок","Прочитать NDEF","Сохранить дамп","Сохранить LF-сигнал"},new Runnable[]{this::readBlock,this::readNdef,this::dumpTag,()->{Map<String,String> p=new HashMap<>();p.put("path",dumpName("lf-signal"));actions.run("data.save",p,120);}});
        box.addView(text("Classic: для полного дампа импортируйте BIN-файл всех ключей через кнопку ниже. Поле одного ключа используется для блока и NDEF. Неполный дамп отмечается в журнале.",14));
        box.addView(button("Импорт файла ключей Classic",()->pick(4)));shownTagRevision=-1;refreshTag();
    }
    void refreshTag(){if(family==null){if(currentPage==1)page(1);return;}
        if(service==null||tagText==null)return;
        org.proxmarkdesk.android.session.SessionState ss=service.sessionState;
        shownTagRevision=ss.revision;TagInfo tag=service.tagInfo;
        if(tag==null){tagText.setText(ss.isBusy()?"Поиск и сбор сведений… Держите одну метку на антенне.":"Выполните поиск: семейство и сведения будут заполнены автоматически.");return;}
        tagText.setText((service.tagCached?"Из истории — метка сейчас не подтверждена\n":"")+tag.summary());
        if(tag.detected&&!tag.ambiguous){family.setSelection(tag.family);if(tag.classicSize>=0)cardSize.setSelection(tag.classicSize);String id=RfidAuth.clean(tag.id);if(!RfidAuth.validUid(id))id="";if(!authUid.getText().toString().equals(id))authUid.setText(id);}
        else if(!tag.detected||tag.ambiguous){authUid.setText("");}
    }
    String key(int... sizes){String k=password.getText().toString().replaceAll("\\s","");boolean ok=false;for(int s:sizes)if(k.length()==s)ok=true;if(!ok||!k.matches("[0-9a-fA-F]+"))throw new IllegalArgumentException("Неверная длина HEX-ключа");rememberPassword(k);return k;}
    int blockNumber(){int b=Integer.parseInt(block.getText().toString());if(b<0||b>255)throw new IllegalArgumentException("Номер блока 0–255");return b;}
    void readBlock(){int f=family.getSelectedItemPosition();Map<String,String> p=new HashMap<>();p.put("blk",String.valueOf(blockNumber()));if(f==1){p.put("keytype",keyType.getSelectedItem().toString().toLowerCase(Locale.ROOT));p.put("keyA",key(12));actions.run("classic.rdbl",p,300);}else if(f==2){if(password.length()>0){p.put("keyA",key(8,32));actions.run("mfu.rdbl.key",p,300);}else actions.run("mfu.rdbl",p,300);}else if(f==3)actions.run("iso15693.rdbl",p,300);else message("Для этого семейства используйте консоль и справку.");}
    void readNdef(){int f=family.getSelectedItemPosition();Map<String,String> p=new HashMap<>();if(f==1){if(password.length()>0){p.put("keyA",key(12));actions.run(keyType.getSelectedItemPosition()==1?"classic.ndef.keyb":"classic.ndef.keya",p,300);}else actions.run("classic.ndef",p,300);}else if(f==2){if(password.length()>0){p.put("keyA",key(8,32));actions.run("mfu.ndef.key",p,300);}else actions.run("mfu.ndef",p,300);}else message("NDEF доступен в этой форме для Classic и Ultralight / NTAG.");}
    String dumpName(String prefix){String uid=service!=null&&service.tagInfo!=null&&!service.tagInfo.ambiguous?RfidAuth.clean(service.tagInfo.id):"";if(!uid.matches("[0-9A-F]{8,20}"))uid="unknown";return "dumps/UID-"+uid+"-"+prefix+"-"+System.currentTimeMillis();}
    void dumpTag(){int f=family.getSelectedItemPosition();Map<String,String> p=new HashMap<>();if(f==2){p.put("path",dumpName("mfu"));if(password.length()>0){p.put("keyA",key(8,32));actions.run("mfu.dump.key",p,300);}else actions.run("mfu.dump",p,300);}else if(f==3){p.put("path",dumpName("iso15693"));actions.run("iso15693.dump",p,300);}else if(f==7){p.put("path",dumpName("t55xx"));actions.run("t55xx.dump",p,300);}else if(f==1){File keys=new File(service.library,"classic-keys.bin");int[] expected={192,60,384,480};if(keys.length()!=expected[cardSize.getSelectedItemPosition()])throw new IllegalArgumentException("Импортируйте файл ключей: "+expected[cardSize.getSelectedItemPosition()]+" байт");Map<String,String> cp=new HashMap<>();cp.put("size",cardSize.getSelectedItem().toString());cp.put("path",dumpName("classic"));actions.run("classic.dump.file",cp,300);}else message("Команды дампа этого семейства доступны в каталоге Iceman.");}
    void sniffPage(){LinearLayout box=content();box.addView(text("Sniff / трассы",21));sniffMode=spinner(box,"Протокол",new String[]{"ISO 14443-A / Classic / NTAG","ISO 14443-B","ISO 15693","FeliCa","iCLASS","Topaz","LF-сигнал"});sniffMode.setSelection(prefs().getInt("sniff",0));if(service!=null&&service.tagInfo!=null&&service.tagInfo.detected&&!service.tagInfo.ambiguous){int mode=sniffForFamily(service.tagInfo.family);if(mode>=0)sniffMode.setSelection(mode);}
        EditText sniffSeconds=input(box,"Длительность авто sniff, секунд",String.valueOf(prefs().getInt("autoSniffSeconds",30)));sniffSeconds.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        EditText sniffDelay=input(box,"Пауза между командами, секунд (рекомендуется 1)",String.valueOf(prefs().getInt("autoSniffDelay",1)));sniffDelay.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        box.addView(button("Авто sniff · ISO 14443-A",()->{int duration=Integer.parseInt(sniffSeconds.getText().toString().trim()),delay=Integer.parseInt(sniffDelay.getText().toString().trim());if(!ready())return;service.autoSniff(duration,delay);prefs().edit().putInt("autoSniffSeconds",duration).putInt("autoSniffDelay",delay).apply();page(3);}));
        box.addView(text("Захват → таймер → hw break → просмотр → подробный разбор CRC/задержек → сохранение sniff.<номер>.trace. Следующая команда ждёт завершения предыдущей и указанную паузу. Подробный разбор выполняется через trace list. Файлы доступны во вкладке Дампы.",13));
        box.addView(button("Автовыбор протокола по найденной метке",this::autoSniff));
        box.addView(button("Разобрать PWD_AUTH из файла",()->pick(5)));
        buttons(box,new String[]{"Начать sniff","Показать / декодировать","Сохранить захват","Справка sniff"},new Runnable[]{()->{int n=sniffMode.getSelectedItemPosition();prefs().edit().putInt("sniff",n).apply();actions.run(sniffActionIds[n],1800);},()->actions.run(traceActionIds[sniffMode.getSelectedItemPosition()],120),()->{int n=sniffMode.getSelectedItemPosition();if(n==6){Map<String,String> p=new HashMap<>();p.put("path",dumpName("lf-sniff"));actions.run("data.save",p,120);}else run(traces[n]+"; trace save -f "+dumpName("hf-sniff"));},()->run(sniffs[sniffMode.getSelectedItemPosition()]+" -h")});
        box.addView(text("Разместите антенну рядом со своим считывателем и меткой. Если sniff удерживает консоль, остановите его кнопкой на устройстве. Сохраните захват до остановки сеанса. HF: .trace, LF: .pm3. Для LF настройки доступны через lf config.",14));
    }
    void keysPage(){LinearLayout box=content();box.addView(text("Ключи и доступ",21));
        if(service==null){box.addView(text("Служба запускается…",14));return;}
        TagInfo tag=service.tagInfo;
        if(tag==null||!tag.detected||tag.ambiguous){box.addView(text("Сначала обнаружьте карту на вкладке «Карта». Старый UID и ключи не подставляются в новую операцию автоматически.",14));box.addView(button("Перейти к карте",()->page(1)));return;}
        box.addView(text((service.tagCached?"Историческая карточка · требуется повторное обнаружение\n":"Текущая карта\n")+tag.summary(),14));
        if(service.tagCached){box.addView(button("Повторно обнаружить карту",()->page(1)));return;}
        box.addView(button("Действия для этой карты",()->page(15)));
        box.addView(button("Полный каталог Iceman",()->page(6)));
    }
    void toolsPage(){LinearLayout box=content();box.addView(text("Инструменты",21));box.addView(text("Профессиональные функции остаются доступны в едином режиме приложения.",14));
        buttons(box,new String[]{"Устройство / USB","Консоль Iceman","Sniff / трассы","PM3 Commands","Эмуляция","BVD Analyzer","Сигналы","Сценарии Lua","Python","Об устройстве"},
            new Runnable[]{()->page(0),()->page(3),()->page(2),()->page(6),()->page(7),()->page(9),()->page(10),()->page(11),()->page(12),()->page(8)});
    }
    void consolePage(){
        progressBar=null;progressText=null;
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);body.addView(box,new LinearLayout.LayoutParams(-1,-1));
        TextView hint=text("Введите команду. Например: hw version — версия клиента; hf search — поиск HF-метки через USB.",13);hint.setPadding(dp(10),dp(6),dp(10),0);box.addView(hint);
        command=themedEditText();command.setSingleLine();command.setHint("Команда Iceman");command.setText(prefs().getString("draft",""));box.addView(command);

        // Progress bar (hidden until a long command runs)
        progressBar=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progressBar.setMax(100);progressBar.setVisibility(android.view.View.GONE);box.addView(progressBar);
        progressText=text("",12);progressText.setPadding(dp(10),0,dp(10),0);box.addView(progressText);

        LinearLayout row=new LinearLayout(this);
        org.proxmarkdesk.android.session.SessionState ss=service==null?org.proxmarkdesk.android.session.SessionState.INITIAL:service.sessionState;
        Button execute=button("Выполнить",()->{
            org.proxmarkdesk.android.session.SessionState s=service==null?org.proxmarkdesk.android.session.SessionState.INITIAL:service.sessionState;
            String c=command.getText().toString();String error=ClientInput.error(c);if(error!=null){command.setError(error);return;}
            prefs().edit().putString("draft",c).apply();
            if(ready()&&!s.isBusy()&&s.isClientAlive()){pendingCommand=c;pendingRevision=s.revision;service.run(c,300);}
        });
        execute.setEnabled(ClientInput.error(command.getText().toString())==null);
        command.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){execute.setEnabled(ClientInput.error(s.toString())==null);prefs().edit().putString("draft",s.toString()).apply();}public void afterTextChanged(Editable e){}});
        row.addView(execute);

        row.addView(button("Экспорт",()->{if(ready())export(service.console().getBytes(StandardCharsets.UTF_8),"console.txt");}));box.addView(row);
        consoleScroll=new ScrollView(this);HorizontalScrollView horizontal=new HorizontalScrollView(this);console=text("",12);displayedConsole=service==null?"":service.console();console.setText(coloredConsole(displayedConsole));console.setTypeface(Typeface.MONOSPACE);console.setTextColor(Color.rgb(197,231,231));console.setBackgroundColor(INK);console.setPadding(dp(10),dp(10),dp(10),dp(10));horizontal.addView(console);consoleScroll.addView(horizontal);box.addView(consoleScroll,new LinearLayout.LayoutParams(-1,0,1));lastRevision=-1;
    }
    File emulationFile; boolean checkingUpdates;
    void manageDump(File file){new AlertDialog.Builder(this).setTitle(file.getName()).setItems(new String[]{"Открыть и анализировать","Эмулировать","Экспорт","Переименовать","Добавить UID в имя","Удалить"},(d,which)->guard(()->{
        try{if(which==0)openDump(file);else if(which==1){emulationFile=file;page(7);}else if(which==2)export(Files.readAllBytes(file.toPath()),file.getName());
        else if(which==3){EditText name=themedEditText();name.setSingleLine();name.setText(file.getName());new AlertDialog.Builder(this).setTitle("Новое имя").setView(name).setPositiveButton("Сохранить",(x,w)->guard(()->{try{if(service.sessionState.isBusy())throw new IOException("Дождитесь завершения операции");File dest=DumpLibrary.child(service.dumps,name.getText().toString().trim());if(dest.exists())throw new IOException("Такой файл уже существует");Files.move(file.toPath(),dest.toPath());page(4);}catch(Exception e){message(e.getMessage());}})).setNegativeButton("Отмена",null).show();}
        else if(which==4){if(service.sessionState.isBusy())throw new IOException("Дождитесь завершения операции");String uid=DumpLibrary.uid(file);if(uid.isEmpty())throw new IOException("UID не определён по содержимому. Можно переименовать вручную");DumpLibrary.withUid(file);page(4);}
        else new AlertDialog.Builder(this).setTitle("Удалить дамп?").setMessage(file.getName()).setPositiveButton("Удалить",(x,w)->guard(()->{try{if(service.sessionState.isBusy())throw new IOException("Дождитесь завершения операции");Files.delete(file.toPath());page(4);}catch(Exception e){message(e.getMessage());}})).setNegativeButton("Отмена",null).show();
        }catch(Exception e){message(e.getMessage());}
    })).show();}
    void emulationPage(){LinearLayout box=content();box.addView(text("Эмуляция сохранённого дампа",21));if(!ready())return;
        box.addView(text(service.emulationName.isEmpty()?"Выберите файл и тип метки":"Последний запуск: "+service.emulationName,14));
        List<File> files=new ArrayList<>();File[] all=service.dumps.listFiles();if(all!=null){Arrays.sort(all,(a,b)->a.getName().compareToIgnoreCase(b.getName()));for(File f:all)if(f.isFile()&&f.getName().toLowerCase(Locale.ROOT).matches(".*\\.(bin|json|eml)$"))files.add(f);}
        if(files.isEmpty()){box.addView(text("Нет дампов. Сохраните метку или импортируйте файл на вкладке Дампы.",15));return;}
        String[] names=new String[files.size()];for(int i=0;i<names.length;i++)names[i]=files.get(i).getName();Spinner file=spinner(box,"Дамп",names);if(emulationFile!=null)for(int i=0;i<files.size();i++)if(files.get(i).equals(emulationFile))file.setSelection(i);
        Spinner type=spinner(box,"Тип эмуляции",new String[]{"Classic Mini","Classic 1K","Classic 2K","Classic 4K","Ultralight","Ultralight EV1 / NTAG","Ultralight C","Ultralight AES","ISO15693"});type.setSelection(5);
        box.addView(text("Тип выбирается по вашему дампу. Для Classic нужен полный объём памяти. Неполные или защищённые дампы могут не воспроизводить поведение оригинала. UID берётся из памяти файла.",14));
        box.addView(button("Загрузить дамп и запустить",()->{service.emulate(files.get(file.getSelectedItemPosition()),type.getSelectedItemPosition());page(3);}));
        box.addView(button("Справка выбранного режима",()->run(type.getSelectedItemPosition()<4?"hf mf sim -h":type.getSelectedItemPosition()<8?"hf mfu sim -h":"hf 15 sim -h")));
        box.addView(text("Для аварийного прерывания завершите сеанс или используйте кнопку на Proxmark3. Память метки не записывается: файл загружается в память эмулятора устройства. Для других протоколов используйте каталог команд sim/eload.",14));
    }
    void aboutPage(){LinearLayout box=content();box.addView(text("Об устройстве и обновления",21));if(!ready())return;
        box.addView(text("ProxmarkDesk BVD "+appVersion()+" · Android "+Build.VERSION.RELEASE+" · "+Arrays.toString(Build.SUPPORTED_ABIS)+"\nВстроенный Iceman "+CliCompat.CURRENT_VERSION+" · PM3 Easy / GENERIC",15));
        box.addView(button("Прочитать информацию устройства",()->actions.run("hw.version",120)));
        box.addView(button("Состояние оборудования",()->actions.run("hw.status",120)));
        box.addView(button("Проверить антенны",()->actions.run("hw.tune",120)));
        box.addView(button("Экспорт информации",()->export(service.deviceInfo.getBytes(StandardCharsets.UTF_8),"proxmark-device-info.txt")));
        box.addView(text(service.deviceInfo.isEmpty()?"Информация появится после подключения или hw version.":service.deviceInfo,12));
        TextView updates=text(prefs().getString("updateResult","Обновления ещё не проверялись"),14);box.addView(updates);
        box.addView(button("Проверить обновления Iceman онлайн",()->{if(checkingUpdates)return;checkingUpdates=true;updates.setText("Запрос официального GitHub API…");String device=service.deviceInfo;
            new Thread(()->{String report;try{String firmware="";java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?im)^.*?OS\\.+\\s*(.+)$").matcher(device);if(m.find())firmware=UpdateChecker.version(m.group(1));report="Проверено: "+new Date()+"\n"+UpdateChecker.describe(UpdateChecker.fetch(),CliCompat.CURRENT_VERSION,firmware);prefs().edit().putString("updateResult",report).apply();}catch(Exception e){report="Проверка не выполнена: "+e.getMessage()+"\nСостояние обновлений неизвестно. Проверьте доступ к интернету.";}String result=report;runOnUiThread(()->{checkingUpdates=false;if(!isDestroyed())updates.setText(result);});},"iceman-updates").start();
        }));
        box.addView(button("Официальные релизы и файлы",()->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(UpdateChecker.RELEASES)))));
        box.addView(text("Проверяются стабильные релизы Iceman для клиента и прошивки. Канал обновлений APK ProxmarkDesk пока не опубликован. Прошивка автоматически не устанавливается.\n\nВстроены Lua и команды Iceman; Python, Qt и native Bluetooth в этой ARM64-сборке отсутствуют. Функции RDV4/SPIFFS/смарт-карт зависят от оборудования и могут быть недоступны на Easy.",14));
    }
    void filesPage(){LinearLayout box=content();box.addView(text("Дампы и анализ",21));box.addView(button("Импорт BIN / JSON / EML / сигнала",()->pick(1)));box.addView(button("Обновить",()->page(4)));if(!ready())return;
        EditText search=input(box,"Поиск по UID или имени","");LinearLayout list=new LinearLayout(this);list.setOrientation(1);box.addView(list);
        Runnable filter=()->{list.removeAllViews();File[] files=service.dumps.listFiles();if(files!=null){Arrays.sort(files,(x,y)->Long.compare(y.lastModified(),x.lastModified()));for(File f:files)if(f.isFile()&&f.getName().toLowerCase(Locale.ROOT).contains(search.getText().toString().toLowerCase(Locale.ROOT)))list.addView(button(f.getName()+" · "+f.length()+" Б",()->manageDump(f)));}};
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){filter.run();}public void afterTextChanged(Editable e){}});filter.run();
    }
    void openDump(File f){if(f.getName().toLowerCase(Locale.ROOT).endsWith(".trace")){features.trace(f);return;}selectedFile=f;body.removeAllViews();LinearLayout box=content();box.addView(text(f.getName(),17));box.addView(button("Экспорт исходного файла",()->{try{export(Files.readAllBytes(f.toPath()),f.getName());}catch(Exception ex){message(ex.getMessage());}}));
        if(f.getName().endsWith(".pm3")){try{String[] lines=new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8).split("\\R");float[] values=new float[Math.min(lines.length,1000000)];for(int i=0;i<values.length;i++)values[i]=Float.parseFloat(lines[i].trim());box.addView(new SignalView(values),new LinearLayout.LayoutParams(-1,dp(260)));box.addView(text("Сигнал: "+values.length+" отсчётов",14));}catch(Exception ex){box.addView(text("Формат сигнала не распознан: "+ex.getMessage(),14));}return;}
        try{selectedBytes=DumpAnalyzer.load(f);}catch(Exception ex){box.addView(text("Нет автоматического разбора: "+ex.getMessage()+". Используйте команду view/load клиента.",14));return;}
        layout=spinner(box,"Разметка",new String[]{"Исходные байты","Type 2 / NTAG / Ultralight","MIFARE Classic","BIN MFU: заголовок 56 байт"});
        analysisText=text("",12);analysisText.setTypeface(Typeface.MONOSPACE);box.addView(button("Анализ",this::analyze));box.addView(button("HEX",()->analysisText.setText(DumpAnalyzer.dump(payload()))));box.addView(button("Сравнить с файлом",()->pick(2)));box.addView(button("Экспорт памяти BIN",()->export(payload(),"memory.bin")));box.addView(button("Экспорт отчёта",()->export(analysisText.getText().toString().getBytes(StandardCharsets.UTF_8),"analysis.txt")));box.addView(analysisText);analyze();
    }
    byte[] payload(){if(selectedBytes==null)throw new IllegalArgumentException("Выберите дамп");if(layout!=null&&layout.getSelectedItemPosition()==3){if(selectedBytes.length<56)throw new IllegalArgumentException("Файл короче заголовка");return Arrays.copyOfRange(selectedBytes,56,selectedBytes.length);}return selectedBytes;}
    void analyze(){try{int mode=layout.getSelectedItemPosition();analysisText.setText(DumpAnalyzer.describe(payload(),mode==3?1:mode));}catch(Exception ex){message(ex.getMessage());}}
    void journalPage(){features.history();} void legacyJournalPage(){LinearLayout box=content();box.addView(text("Журнал операций",21));if(!ready())return;File[] files=service.operations.listFiles((dir,name)->name.endsWith(".json"));if(files==null)return;Arrays.sort(files,(a,b)->b.getName().compareTo(a.getName()));for(File f:files){try{JSONObject op=new JSONObject(new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));box.addView(button(op.optString("status")+" · "+op.optString("command"),()->{File log=new File(service.operations,f.getName().replace(".json",".log"));try{byte[] bytes=Files.readAllBytes(log.toPath());new AlertDialog.Builder(this).setTitle(op.optString("status")).setMessage(new String(bytes,0,Math.min(bytes.length,100000),StandardCharsets.UTF_8)).setPositiveButton("Экспорт",(d,w)->export(bytes,log.getName())).setNegativeButton("Закрыть",null).show();}catch(Exception ex){message(ex.getMessage());}}));}catch(Exception ignored){}}}
    int catalogueLimit=100;
    void commandDialog(String name){EditText input=themedEditText();input.setSingleLine();input.setText(name+" ");new AlertDialog.Builder(this).setTitle("Команда и параметры").setView(input).setPositiveButton("Выполнить",(d,w)->guard(()->run(input.getText().toString(),300))).setNegativeButton("Отмена",null).show();}
    void cataloguePage(){readingPage.show(true);} void legacyCataloguePage(){LinearLayout box=content();box.addView(text("Команды встроенного Iceman",21));box.addView(text("Полный каталог встроенного клиента: чтение, запись, эмуляция, анализ, LF/HF, Lua и аппаратные команды. Ищите sim, dump, restore, wrbl, script, data или семейство. Параметры — через справку -h. Команды выполняются с возможностями вашей прошивки и оборудования.",14));box.addView(button("Lua-скрипты",()->run("script list")));box.addView(button("Справка Lua",()->run("script run -h")));box.addView(button("Получить каталог",()->{if(ready()){service.catalogue();page(3);}}));if(service==null)return;EditText search=input(box,"Поиск команды","");LinearLayout results=new LinearLayout(this);results.setOrientation(1);box.addView(results);try{commands.clear();for(String line:new String(Files.readAllBytes(new File(service.library,"commands.txt").toPath()),StandardCharsets.UTF_8).split("\\R"))if(line.matches("^\\s*[a-z0-9_][a-z0-9_ .-]*?\\s*\\|\\s*[YN]\\s*\\|.*"))commands.add(line.trim());}catch(Exception ignored){}
        Runnable filter=()->{results.removeAllViews();int shown=0;for(String c:commands)if(c.toLowerCase(Locale.ROOT).contains(search.getText().toString().toLowerCase(Locale.ROOT))){if(shown++>=catalogueLimit)break;String name=c.split("\\|")[0].trim();results.addView(button(c,()->new AlertDialog.Builder(this).setTitle(name).setItems(new String[]{"Вставить в консоль","Справка -h","Запустить с параметрами"},(d,w)->{if(w==2){commandDialog(name);return;}prefs().edit().putString("draft",name+(w==1?" -h":"")).apply();page(3);if(w==1&&service.sessionState.isClientAlive())service.run(name+" -h",30);}).show()));}results.addView(text("Всего: "+commands.size()+". Показано до "+catalogueLimit+" совпадений.",12));};search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){filter.run();}public void afterTextChanged(Editable e){}});filter.run();box.addView(button("Показать ещё 100",()->{catalogueLimit+=100;filter.run();}));
    }

    File dictionaryDir(){File d=new File(service==null?new File(getFilesDir(),"library"):service.library,"dictionaries");d.mkdirs();return d;}
    File dictionaryFile(String actionId){String safe=(actionId==null?"dictionary":actionId).replaceAll("[^A-Za-z0-9._-]","_");return new File(dictionaryDir(),safe+".dic");}
    String dictionaryPath(String actionId){File f=dictionaryFile(actionId);return f.isFile()&&f.length()>0?f.getAbsolutePath():"";}
    String dictionaryDisplayName(String actionId){return prefs().getString("dictName:"+actionId,"");}
    void pickDictionary(String actionId){pendingDictionaryAction=actionId==null?"":actionId;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,33);}
    void pick(int action){fileAction=action;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,20);}
    void showAuthTrace(byte[] bytes,String name){
        List<RfidAuth.Candidate> found=RfidAuth.analyze(RfidAuth.readTrace(bytes,name.toLowerCase(Locale.ROOT).endsWith(".trace")));
        StringBuilder report=new StringBuilder("PWD_AUTH · "+name+"\n");for(RfidAuth.Candidate c:found)report.append(c).append('\n');
        report.append("Записей: ").append(found.size()).append(". PACK с CRC подтверждает ответ в этой трассе; принадлежность UID требует полного SELECT. Неизвестный UID не переносится на другую метку.");
        body.removeAllViews();LinearLayout box=content();box.addView(text(report.toString(),14));box.addView(button("Экспорт отчёта",()->export(report.toString().getBytes(StandardCharsets.UTF_8),"pwd-auth-report.txt")));
        for(RfidAuth.Candidate c:found)if(RfidAuth.validUid(c.uid))box.addView(button("Сохранить PWD для UID "+c.uid+" · "+c.password,()->{prefs().edit().putString("draftPwdUid",c.uid).putString("draftPwd",c.password).apply();message("PWD сохранён как кандидат для UID "+c.uid+". Повторно обнаружьте эту метку перед использованием.");}));
    }
    void exportFile(File f){export(null,f.getName());exportSource=f;} void export(byte[] bytes,String name){exportSource=null;exportBytes=bytes;exportName=name;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/octet-stream");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(i,21);}
    static byte[] readStream(InputStream stream,int max)throws IOException{try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))>=0){if(out.size()+n>max)throw new IOException("Файл превышает лимит "+max);out.write(b,0,n);}return out.toByteArray();}}
    @Override public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return;if(request==FirmwarePage.PICK){firmwarePage.importPackage(data.getData());return;}guard(()->{try{if(request==33){if(!ready())return;String name="dictionary.dic";try(android.database.Cursor c=getContentResolver().query(data.getData(),new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}byte[] bytes=readStream(getContentResolver().openInputStream(data.getData()),8*1024*1024);if(bytes.length==0)throw new IOException("Файл словаря пуст");String action=pendingDictionaryAction;if(action.isEmpty())throw new IOException("Не выбрано действие для словаря");File dest=dictionaryFile(action);Files.write(dest.toPath(),bytes);prefs().edit().putString("dictName:"+action,new File(name).getName()).apply();pendingDictionaryAction="";page(15);message("Словарь добавлен: "+name+" · "+bytes.length+" байт");return;}if(request==32){if(!ready())return;String name="import.py";try(android.database.Cursor c=getContentResolver().query(data.getData(),new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}if(!name.toLowerCase(Locale.ROOT).endsWith(".py"))throw new IOException("Выберите файл .py");name=LibraryMirror.profile(name.substring(0,name.length()-3)).replace("..","_")+".py";File dir=new File(service.library,"scripts/python");dir.mkdirs();File dest=new File(dir,name);if(dest.exists())dest=new File(dir,System.currentTimeMillis()+"-"+name);byte[] bytes=readStream(getContentResolver().openInputStream(data.getData()),1024*1024);Files.write(dest.toPath(),bytes);service.requestMirror();page(12);message("Скрипт импортирован: "+dest.getName()+". Для выполнения выберите его в списке.");return;}if(request==31){getContentResolver().takePersistableUriPermission(data.getData(),data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));prefs().edit().putString("mirrorUri",data.getData().toString()).apply();if(service!=null)service.requestMirror();return;}if(request==21){try(OutputStream out=getContentResolver().openOutputStream(data.getData(),"wt")){if(exportSource!=null){try(InputStream in=new FileInputStream(exportSource)){byte[] chunk=new byte[16384];int n;while((n=in.read(chunk))!=-1)out.write(chunk,0,n);}}else out.write(exportBytes);}Toast.makeText(this,"Сохранено",Toast.LENGTH_SHORT).show();return;}byte[] bytes=readStream(getContentResolver().openInputStream(data.getData()),8*1024*1024);String name="import.bin";try(android.database.Cursor cursor=getContentResolver().query(data.getData(),new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);}name=new File(name).getName();File imported=new File(service.dumps,System.currentTimeMillis()+"-"+name);if(fileAction==6){Files.write(imported.toPath(),bytes);editBvdProfile(imported);}else if(fileAction==7){bvdTrace=bytes;bvdTraceBinary=name.toLowerCase(Locale.ROOT).endsWith(".trace");bvdReport=BvdProfiles.trace(bytes,bvdTraceBinary,null,false);showBvdReport();}else if(fileAction==8){bvdReport=BvdProfiles.trace(bvdTrace,bvdTraceBinary,bytes,name.toLowerCase(Locale.ROOT).endsWith(".trace"));showBvdReport();}else if(fileAction==5){showAuthTrace(bytes,name);}else if(fileAction==4){Files.write(new File(service.library,"classic-keys.bin").toPath(),bytes);message("Файл ключей импортирован: "+bytes.length+" байт");}else{Files.write(imported.toPath(),bytes);if(fileAction==2){analysisText.setText(DumpAnalyzer.compare(payload(),DumpAnalyzer.load(imported)));}else {try{imported=DumpLibrary.withUid(imported);}catch(Exception ignored){}openDump(imported);}}}catch(Exception ex){message(ex.getMessage());}});}
    class SignalView extends View{final float[] values;final Paint paint=new Paint();SignalView(float[] d){super(MainActivity.this);values=d;}protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.drawColor(INK);paint.setColor(Color.rgb(91,219,202));paint.setStrokeWidth(1);float mid=getHeight()/2f;for(int x=0;x<getWidth();x++){int start=x*values.length/Math.max(1,getWidth()),end=Math.min(values.length,Math.max(start+1,(x+1)*values.length/Math.max(1,getWidth())));float min=Float.MAX_VALUE,max=-Float.MAX_VALUE;for(int i=start;i<end;i++){min=Math.min(min,values[i]);max=Math.max(max,values[i]);}if(start<end)canvas.drawLine(x,mid-min*mid/128,x,mid-max*mid/128,paint);}}}
    @Override public void onDestroy(){features.close();unregisterReceiver(usbEvents);timer.removeCallbacks(refresh);if(bound)unbindService(connection);super.onDestroy();}
}
