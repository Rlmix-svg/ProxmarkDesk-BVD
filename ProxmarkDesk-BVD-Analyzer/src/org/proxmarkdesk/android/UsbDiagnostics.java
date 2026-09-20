// SPDX-License-Identifier: GPL-3.0-or-later
package org.proxmarkdesk.android;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Read-only diagnostics. Never changes permissions, SELinux, drivers or USB roles. */
public final class UsbDiagnostics {
    public static final String SCRIPT =
        "export PATH=/system/bin:/system/xbin:/vendor/bin:$PATH\n"+
        "echo PMD_BEGIN\n"+
        "printf 'PMD_UID='; id -u\n"+
        "id\n"+
        "ls -ld /dev /sys/class/tty /sys/bus/usb/devices\n"+
        "if ls /dev >/dev/null; then echo PMD_DEV_LIST_OK; fi\n"+
        "if ls /sys/bus/usb/devices >/dev/null; then echo PMD_USB_LIST_OK; fi\n"+
        "for p in /dev/ttyACM* /dev/ttyUSB*; do if [ -c \"$p\" ]; then echo \"PMD_TTY=$p\"; ls -lZ \"$p\"; fi; done\n"+
        "for p in /sys/class/tty/ttyACM* /sys/class/tty/ttyUSB*; do if [ -e \"$p\" ]; then echo \"PMD_SYS_TTY=$p\"; ls -l \"$p/device/driver\"; fi; done\n"+
        "for p in /sys/bus/usb/devices/*; do if [ -r \"$p/idVendor\" ] && [ -r \"$p/idProduct\" ]; then printf 'PMD_USB='; tr -d '\\n' < \"$p/idVendor\"; printf ':'; cat \"$p/idProduct\"; fi; done\n"+
        "for p in /sys/class/usb_role/*/role /sys/class/typec/port*/data_role; do if [ -r \"$p\" ]; then printf 'PMD_ROLE=%s:' \"$p\"; cat \"$p\"; fi; done\n"+
        "echo PMD_END\n";

    public static class Capture {
        public final int exitCode; public final boolean timedOut; public final String output;
        public Capture(int code,boolean timeout,String text){exitCode=code;timedOut=timeout;output=text;}
    }
    public static Capture capture(ProcessBuilder builder,int seconds)throws Exception {
        Process process=builder.redirectErrorStream(true).start();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        Thread drain=new Thread(()->{try(InputStream input=process.getInputStream()){
            byte[] data=new byte[4096];int n;
            while((n=input.read(data))!=-1){synchronized(bytes){int keep=Math.min(n,65536-bytes.size());if(keep>0)bytes.write(data,0,keep);}}
        }catch(IOException ignored){}},"usb-diagnostics-output");
        drain.setDaemon(true);drain.start();
        try{
            boolean ended=process.waitFor(seconds,TimeUnit.SECONDS);
            if(!ended)process.destroyForcibly();
            drain.join(2000);
            synchronized(bytes){return new Capture(ended?process.exitValue():-1,!ended,bytes.toString("UTF-8"));}
        }finally{if(process.isAlive())process.destroyForcibly();process.getInputStream().close();}
    }
    public static class Result {
        public final ArrayList<String> ports=new ArrayList<>();
        public boolean complete,root,devReadable,usbReadable; public int sysTty,sysUsb;
        public String summary,report;
    }
    public static Result parse(Capture capture,boolean requestedRoot,int apiCount,String apiDetails){
        Result r=new Result();boolean begin=false,end=false;
        for(String line:capture.output.split("\\R")){
            line=line.trim();
            if(line.equals("PMD_BEGIN"))begin=true;
            if(line.equals("PMD_END"))end=true;
            if(line.equals("PMD_UID=0"))r.root=true;
            if(line.equals("PMD_DEV_LIST_OK"))r.devReadable=true;
            if(line.equals("PMD_USB_LIST_OK"))r.usbReadable=true;
            if(line.matches("PMD_TTY=/dev/tty(?:ACM|USB)[0-9]+")){
                String port=line.substring(8);if(!r.ports.contains(port))r.ports.add(port);
            }
            if(line.matches("PMD_SYS_TTY=/sys/class/tty/tty(?:ACM|USB)[0-9]+"))r.sysTty++;
            if(line.matches("PMD_USB=[0-9a-fA-F]{4}:[0-9a-fA-F]{4}"))r.sysUsb++;
        }
        r.complete=begin&&end&&!capture.timedOut&&capture.exitCode==0;
        Collections.sort(r.ports);
        if(capture.timedOut)r.summary="Диагностика не завершилась за отведённое время. Проверьте запрос root.";
        else if(!r.complete)r.summary="Не удалось завершить диагностику (код "+capture.exitCode+"). Отсутствие портов не подтверждено; подробности в отчёте.";
        else if(requestedRoot&&!r.root)r.summary="Root не подтверждён: id -u не вернул 0. Проверьте разрешение ProxmarkDesk в менеджере root.";
        else if(!r.ports.isEmpty())r.summary="Найдены последовательные порты: "+String.join(", ",r.ports)+". Это кандидаты; связь с Proxmark3 проверяется кнопкой «Подключить».";
        else if(!requestedRoot)r.summary="Без root tty-порты не найдены. Включите root и повторите проверку.";
        else if(!r.devReadable||!r.usbReadable||apiCount<0)r.summary="Root подтверждён, но не все системные пути или USB API доступны для проверки. Отсутствие USB не подтверждено; нужен отчёт диагностики.";
        else if(r.sysTty>0)r.summary="В sysfs есть последовательный интерфейс, но доступный узел /dev/ttyACM* или ttyUSB* не найден. Нужен отчёт диагностики.";
        else if(apiCount>0||r.sysUsb>0)r.summary="USB-устройства видны, но ttyACM/ttyUSB не найден. Это ещё не подтверждает обнаружение Proxmark3: проверьте VID/PID и драйвер в отчёте.";
        else r.summary="Root подтверждён, но USB-устройства и tty-порты не обнаружены. Проверьте режим OTG, питание и кабель; переподключите Proxmark3 и повторите поиск.";
        r.report="ProxmarkDesk 0.4 — диагностика USB\n"+new Date()+"\n"+r.summary+
            "\n\nAndroid USB API: "+(apiCount<0?"ошибка перечисления":apiCount+" устройств")+"\n"+apiDetails+
            "\nRoot запрошен: "+requestedRoot+"; uid=0: "+r.root+
            "\nКод завершения: "+capture.exitCode+"; тайм-аут: "+capture.timedOut+"; полный ответ: "+r.complete+
            "\n\nВывод проверки:\n"+capture.output;
        return r;
    }
    public static Result run(boolean root,int count,String details)throws Exception {
        Capture result=capture(root?new ProcessBuilder("su","-c",SCRIPT):new ProcessBuilder("/system/bin/sh","-c",SCRIPT),30);
        return parse(result,root,count,details);
    }
}
