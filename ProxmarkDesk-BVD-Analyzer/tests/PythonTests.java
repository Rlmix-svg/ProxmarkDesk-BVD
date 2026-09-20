package org.proxmarkdesk.android;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import org.json.*;
public final class PythonTests {
    static int checks;static void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
    public static void main(String[] args)throws Exception {
        String python=args[0];File module=new File(args[1]);
        check(PythonRunner.arguments("--file \"dumps/мой файл.bin\" '' 'a b'").equals(Arrays.asList("--file","dumps/мой файл.bin","","a b")),"quoted Unicode arguments and empty argument");
        check(PythonRunner.arguments("C:\\a\\b.py").get(0).equals("C:\\a\\b.py"),"backslashes preserved; no shell expansion");
        boolean rejected=false;try{PythonRunner.arguments("\"unfinished");}catch(IllegalArgumentException e){rejected=true;}check(rejected,"unclosed quote rejected");
        rejected=false;try{PythonRunner.arguments("a\nb");}catch(IllegalArgumentException e){rejected=true;}check(rejected,"multiline arguments rejected");
        StringBuilder output=new StringBuilder();int code=new PythonRunner().run(new ProcessBuilder(python,"-u","-c","import sys; print('Привет 🐍'); print('ошибка',file=sys.stderr)"),20,output::append);
        check(code==0&&output.toString().contains("Привет 🐍")&&output.toString().contains("ошибка"),"UTF-8 stdout and stderr captured");
        check(new PythonRunner().run(new ProcessBuilder(python,"-c","raise SystemExit(7)"),20,s->{})==7,"nonzero exit preserved");
        output.setLength(0);rejected=false;try{new PythonRunner().run(new ProcessBuilder(python,"-u","-c","import time; print('partial'); time.sleep(30)"),1,output::append);}catch(TimeoutException e){rejected=true;}check(rejected&&output.toString().contains("partial"),"timeout preserves partial output");
        PythonRunner cancelled=new PythonRunner();CountDownLatch started=new CountDownLatch(1);AtomicReference<Throwable> result=new AtomicReference<>();Thread run=new Thread(()->{try{cancelled.run(new ProcessBuilder(python,"-u","-c","import time; print('started'); time.sleep(30)"),30,s->started.countDown());}catch(Throwable t){result.set(t);}});run.start();check(started.await(10,TimeUnit.SECONDS),"cancellation fixture started");cancelled.cancel();run.join(5000);check(!run.isAlive()&&result.get() instanceof CancellationException,"cancel terminates script promptly");
        AtomicInteger calls=new AtomicInteger();
        try(PythonBridge bridge=new PythonBridge(request->{calls.incrementAndGet();String action=request.getString("action");if(action.equals("input"))return new JSONObject().put("value","ответ");String cmd=request.getString("command");if(cmd.equals("bad"))throw new IOException("fixture failure");return new JSONObject().put("output","Iceman ответ: "+cmd+"\n").put("returncode",0);})){
            try(Socket s=new Socket("127.0.0.1",bridge.port())){s.setSoTimeout(5000);s.getOutputStream().write("{\"token\":\"wrong\",\"action\":\"command\"}\n".getBytes(StandardCharsets.UTF_8));String response=PythonBridge.readLine(s.getInputStream(),4096);check(!new JSONObject(response).getBoolean("ok")&&calls.get()==0,"unauthorized requests never reach PM3 handler");}
            File probe=Files.createTempFile("pm3-python-probe-",".py").toFile();Files.write(probe.toPath(),("import pm3\np=pm3.pm3()\nassert p.console('hw version')==0\nassert 'Iceman ответ' in p.grabbed_output\nassert input('Введите значение')=='ответ'\ntry:\n p.console('bad')\nexcept RuntimeError as e:\n assert 'fixture failure' in str(e)\nelse:\n raise AssertionError('missing error')\nprint('BRIDGE PASS')\n").getBytes(StandardCharsets.UTF_8));
            ProcessBuilder pb=new ProcessBuilder(python,"-u",new File(module,"bootstrap.py").getAbsolutePath(),probe.getAbsolutePath());pb.environment().put("PMDESK_TOKEN",bridge.token);pb.environment().put("PMDESK_PORT",String.valueOf(bridge.port()));pb.environment().put("PYTHONUTF8","1");output.setLength(0);
            code=new PythonRunner().run(pb,30,output::append);check(code==0&&output.toString().contains("BRIDGE PASS")&&calls.get()==3,"Python adapter: command, captured output, input and errors");
        }
        System.out.println("PASS Python checks: "+checks);
    }
}
