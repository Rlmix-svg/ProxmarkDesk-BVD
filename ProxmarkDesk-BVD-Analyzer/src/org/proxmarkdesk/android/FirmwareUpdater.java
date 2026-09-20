// SPDX-License-Identifier: GPL-3.0-or-later
package org.proxmarkdesk.android;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

/** Safe PM3GENERIC firmware update orchestration for the embedded v4.23346 client. */
public final class FirmwareUpdater {
    public interface Listener { void line(String text); void status(String text); }
    private final File exe, workDir, logFile;
    private final String port;
    private final Listener listener;

    public FirmwareUpdater(File exe, File workDir, File logFile, String port, Listener listener) {
        this.exe=exe; this.workDir=workDir; this.logFile=logFile; this.port=port; this.listener=listener;
    }

    public void execute(FirmwarePackage pack, boolean bootOnly) throws Exception {
        pack.recheck();
        if (!exe.isFile()) throw new IOException("Встроенный клиент не найден");
        if (!port.matches("/dev/tty(?:ACM|USB)[0-9]+")) throw new IOException("Неверный tty-порт");
        resetLog();
        log("Firmware package: "+pack.version);
        log("Port: "+port);

        if (bootOnly) {
            if (pack.bootrom==null) throw new IOException("Пакет не содержит bootrom.elf");
            status("Обновление bootrom…");
            flash(pack.bootrom,true);
            status("Ожидание Proxmark3 после bootrom…");
            waitForPort(45);
        } else {
            if (pack.bootrom!=null) {
                status("Этап 1/2 · обновление bootrom…");
                flash(pack.bootrom,true);
                status("Этап 1/2 · ожидание USB после bootrom…");
                waitForPort(45);
            }
            status(pack.bootrom==null?"Обновление fullimage…":"Этап 2/2 · обновление fullimage…");
            flash(pack.fullimage,false);
            status("Ожидание Proxmark3 после fullimage…");
            waitForPort(45);
        }

        status("Проверка установленной версии…");
        String report=hwVersion();
        log("\n=== hw version ===\n"+report);
        verifyVersion(report,pack.version,bootOnly);
        status(bootOnly?"Bootrom "+pack.version+" установлен и проверен":"Прошивка "+pack.version+" установлена и проверена");
        line("[+] Финальная проверка hw version: OK");
    }

    private void flash(File image, boolean boot) throws Exception {
        StringBuilder c=new StringBuilder();
        c.append(q(exe.getAbsolutePath())).append(' ').append(q(port)).append(" --flash ");
        if (boot) c.append("--unlock-bootloader ");
        c.append("--image ").append(q(image.getAbsolutePath()));
        int rc=runRoot(c.toString(),240,true);
        if (rc!=0) throw new IOException(image.getName()+": код завершения "+rc);
        line("[+] "+image.getName()+" записан");
    }

    private void waitForPort(int seconds) throws Exception {
        long end=System.currentTimeMillis()+seconds*1000L;
        boolean disappeared=false;
        while(System.currentTimeMillis()<end) {
            boolean present=rootTest("test -c "+q(port));
            if (!present) disappeared=true;
            if (present && (disappeared || System.currentTimeMillis()>end-seconds*1000L+1500L)) {
                Thread.sleep(1200);
                line("[+] USB доступен: "+port);
                return;
            }
            Thread.sleep(500);
        }
        throw new IOException("тайм-аут ожидания "+port);
    }

    private String hwVersion() throws Exception {
        String command=q(exe.getAbsolutePath())+' '+q(port)+" --incognito --flush --command "+q("hw version");
        StringBuilder out=new StringBuilder();
        int rc=runRootCapture(command,45,out);
        if (rc!=0) throw new IOException("hw version: код завершения "+rc);
        return out.toString();
    }

    private static void verifyVersion(String text,String version,boolean bootOnly) throws IOException {
        String wanted=version.toLowerCase();
        String boot=find(text,"(?im)^\\s*Bootrom\\.*\\s*(.+)$");
        String os=find(text,"(?im)^\\s*OS\\.*\\s*(.+)$");
        if (bootOnly) {
            if (!boot.toLowerCase().contains(wanted)) throw new IOException("bootrom после обновления не подтверждён: "+shortLine(boot));
        } else {
            if (!os.toLowerCase().contains(wanted)) throw new IOException("OS после обновления не подтверждена: "+shortLine(os));
            if (!boot.isEmpty() && !boot.toLowerCase().contains(wanted)) throw new IOException("bootrom после обновления не подтверждён: "+shortLine(boot));
        }
    }
    private static String find(String s,String re){Matcher m=Pattern.compile(re).matcher(s);return m.find()?m.group(1).trim():"";}
    private static String shortLine(String s){return s.isEmpty()?"строка отсутствует":s;}

    private int runRoot(String command,int timeout,boolean stream) throws Exception {
        StringBuilder ignored=new StringBuilder(); return runRootCapture(command,timeout,ignored,stream);
    }
    private int runRootCapture(String command,int timeout,StringBuilder out) throws Exception {return runRootCapture(command,timeout,out,false);}
    private int runRootCapture(String command,int timeout,StringBuilder out,boolean stream) throws Exception {
        Process p=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
        try(BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream(),StandardCharsets.UTF_8))){
            String s; while((s=r.readLine())!=null){out.append(s).append('\n');log(s);if(stream){String clean=ansi(s).trim();if(!clean.isEmpty())line(clean);}}
        }
        if(!p.waitFor(timeout,TimeUnit.SECONDS)){p.destroyForcibly();throw new IOException("тайм-аут процесса ("+timeout+" с)");}
        return p.exitValue();
    }
    private boolean rootTest(String command){try{Process p=new ProcessBuilder("su","-c",command).start();return p.waitFor(3,TimeUnit.SECONDS)&&p.exitValue()==0;}catch(Exception e){return false;}}
    private void resetLog() throws IOException {File parent=logFile.getParentFile();if(parent!=null)parent.mkdirs();try(FileOutputStream o=new FileOutputStream(logFile,false)){}}
    private synchronized void log(String s){try(FileWriter w=new FileWriter(logFile,true)){w.write(s);w.write('\n');}catch(IOException ignored){}}
    private void line(String s){if(listener!=null)listener.line(s);}
    private void status(String s){if(listener!=null)listener.status(s);line("[Обновление] "+s);}
    private static String ansi(String s){return s.replaceAll("\\u001B\\[[;\\d]*[ -/]*[@-~]","");}
    private static String q(String s){return "'"+s.replace("'","'\\''")+"'";}
}
