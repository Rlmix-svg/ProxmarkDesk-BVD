package org.proxmarkdesk.android;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public class RootLaunchTests {
    static int checks;
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    static String path(Path p){return p.toAbsolutePath().toString().replace('\\','/');}
    public static void main(String[] args)throws Exception{
        Path base=Paths.get(args[1]).toAbsolutePath();Files.createDirectories(base);
        String[] names={"install==","install == 'quotes' $(printf BAD) `printf BAD`"};
        for(String name:names){
            Path folder=base.resolve(name);Files.createDirectories(folder);
            Path home=folder.resolve("home space's==");Files.createDirectories(home);
            Path library=folder.resolve("library");Files.createDirectories(library);
            Path executable=folder.resolve("libpm3client.so");
            Files.write(executable,("#!/bin/sh\nprintf 'HOME=%s\\n' \"$HOME\"\nprintf 'PWD=%s\\n' \"$PWD\"\nfor arg in \"$@\"; do printf 'ARG=%s\\n' \"$arg\"; done\n").getBytes(StandardCharsets.UTF_8));
            executable.toFile().setExecutable(true);
            String command=RootLaunch.command(path(library),path(home),path(executable),"/dev/ttyACM0");
            check(!command.contains("exec env "),"no env executable parsing: "+name);
            UsbDiagnostics.Capture result=UsbDiagnostics.capture(new ProcessBuilder(args[0],"-c",command),10);
            if(result.exitCode!=0)throw new AssertionError(result.output);
            check(!result.timedOut,"quoted launch completes: "+name);
            check(result.output.matches("(?s).*PMDESK_PID_[0-9]+\\R.*"),"PID marker preserved: "+name);
            check(result.output.contains("HOME="+path(home)),"HOME preserved literally: "+name);
            check(result.output.contains("ARG=--incognito\nARG=--flush\nARG=--port\nARG=/dev/ttyACM0"),"Iceman argument order: "+name);
            check(result.output.contains("/library\n"),"working directory selected: "+name);
        }
        try{RootLaunch.command("/tmp","/tmp","/bin/client","/dev/ttyACM0; reboot");throw new AssertionError("invalid port accepted");}
        catch(IllegalArgumentException expected){check(true,"invalid port rejected");}
        System.out.println("TOTAL "+checks+" launch checks passed");
    }
}
