package org.proxmarkdesk.android;
import android.content.Context;import java.io.*;import java.nio.file.*;import java.util.*;import java.util.zip.*;

public final class AndroidPython {
    public static final String VERSION="CPython 3.14.7 · ARM64";
    public static final String[] SCRIPTS={"python_selftest.py","device_report.py","dump_inventory.py","pm3_eml2mfd.py","pm3_mfd2eml.py","pm3_nfc2eml.py","findbits.py","parity.py","xorcheck.py","pm3_help2json.py","pm3_help2list.py"};
    public static final String[] DESCRIPTIONS={"Проверка Python и стандартных модулей без устройства","Информация об устройстве через текущий сеанс Iceman","Опись дампов: размеры и SHA-256, отчёт JSON","Преобразование EML в бинарный MFD","Преобразование бинарного MFD в EML","Преобразование NFC-дампа в EML","Поиск числа в битовом потоке","Расчёт чётности","Проверка XOR","Преобразование справки клиента в JSON","Преобразование справки клиента в список"};
    public static String example(String name){switch(name){case "pm3_eml2mfd.py":return "dumps/input.eml dumps/output.mfd";case "pm3_mfd2eml.py":return "dumps/input.mfd dumps/output.eml";case "pm3_nfc2eml.py":return "-i dumps/input.nfc -o dumps/output.eml";case "findbits.py":return "73 0110010101110011";case "parity.py":return "10 1234";case "xorcheck.py":return "04 00 80 64 ba";case "pm3_help2json.py":case "pm3_help2list.py":return "-h";default:return "Аргументы не требуются для встроенного примера";}}
    public static File home(Context context){return new File(context.getFilesDir(),"python-3.14.7-v1");}
    public static synchronized void install(Context context)throws Exception {
        File home=home(context),stamp=new File(home,".ready");if(stamp.isFile()){linkModules(context);return;}home.mkdirs();String prefix=home.getCanonicalPath()+File.separator;
        try(ZipInputStream zip=new ZipInputStream(context.getAssets().open("python-home.zip"))){ZipEntry entry;byte[] b=new byte[16384];while((entry=zip.getNextEntry())!=null){File out=new File(home,entry.getName());if(!out.getCanonicalPath().startsWith(prefix))throw new IOException("Неверный путь Python-ресурса");if(entry.isDirectory()){out.mkdirs();continue;}out.getParentFile().mkdirs();try(OutputStream stream=new FileOutputStream(out)){int n;while((n=zip.read(b))>0)stream.write(b,0,n);}}}
        linkModules(context);ResultCache.atomic(stamp,VERSION);
    }
    static void linkModules(Context context)throws IOException {
        File dir=new File(home(context),"lib/python3.14/lib-dynload");dir.mkdirs();
        File[] libs=new File(context.getApplicationInfo().nativeLibraryDir).listFiles((d,n)->n.startsWith("libpyext_")&&n.endsWith(".so"));if(libs==null||libs.length==0)throw new IOException("Модули Python не извлечены из APK");
        for(File lib:libs){String module=lib.getName().substring("libpyext_".length());Path link=new File(dir,module).toPath(),target=lib.toPath();if(Files.isSymbolicLink(link)&&Files.readSymbolicLink(link).equals(target))continue;Files.deleteIfExists(link);Files.createSymbolicLink(link,target);}
    }
    public static File script(Context context,File library,String name,boolean custom)throws IOException {
        if(!name.matches("[\\p{L}\\p{N}._ -]+\\.py")||name.contains(".."))throw new IOException("Недопустимое имя Python-скрипта");
        if(!custom&&!Arrays.asList(SCRIPTS).contains(name))throw new IOException("Неизвестный встроенный скрипт");
        File directory=custom?new File(library,"scripts/python"):new File(home(context),"scripts");File file=new File(directory,name);
        if(!file.getCanonicalPath().startsWith(directory.getCanonicalPath()+File.separator)||!file.isFile())throw new IOException("Скрипт не найден");return file;
    }
    public static ProcessBuilder process(Context context,File library,File script,List<String> args,PythonBridge bridge)throws IOException {
        String nativeDir=context.getApplicationInfo().nativeLibraryDir;File executable=new File(nativeDir,"libpmdeskpython.so");if(!executable.isFile())throw new IOException("В APK нет Python для этой архитектуры");
        List<String> cmd=new ArrayList<>();cmd.add(executable.getAbsolutePath());cmd.add(new File(home(context),"bootstrap.py").getAbsolutePath());cmd.add(script.getAbsolutePath());cmd.addAll(args);
        ProcessBuilder pb=new ProcessBuilder(cmd).directory(library);Map<String,String> env=pb.environment();env.put("PYTHONHOME",home(context).getAbsolutePath());env.put("PYTHONPATH",home(context).getAbsolutePath());env.put("PYTHONUNBUFFERED","1");env.put("PYTHONUTF8","1");env.put("PYTHONNOUSERSITE","1");env.put("PYTHONDONTWRITEBYTECODE","1");env.put("LD_LIBRARY_PATH",nativeDir);env.put("PMDESK_NATIVE",nativeDir);env.put("PMDESK_LIBRARY",library.getAbsolutePath());env.put("PMDESK_PORT",String.valueOf(bridge.port()));env.put("PMDESK_TOKEN",bridge.token);env.put("HOME",context.getFilesDir().getAbsolutePath());env.put("TMPDIR",context.getCacheDir().getAbsolutePath());return pb;
    }
}
