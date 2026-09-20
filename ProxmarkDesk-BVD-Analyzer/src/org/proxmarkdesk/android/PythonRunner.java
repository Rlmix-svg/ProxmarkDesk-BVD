package org.proxmarkdesk.android;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.*;

/** Process lifetime and output draining, independent of Android UI. */
public final class PythonRunner {
    public interface Output {void write(String value)throws Exception;}
    private volatile Process process;private volatile boolean cancelled;
    public static List<String> arguments(String text){List<String> out=new ArrayList<>();StringBuilder part=new StringBuilder();char quote=0;boolean started=false;for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c=='\0'||c=='\n'||c=='\r')throw new IllegalArgumentException("Аргументы должны быть одной строкой");if(quote!=0){if(c==quote)quote=0;else part.append(c);started=true;}else if(c=='\''||c=='\"'){quote=c;started=true;}else if(Character.isWhitespace(c)){if(started){out.add(part.toString());part.setLength(0);started=false;}}else{part.append(c);started=true;}}if(quote!=0)throw new IllegalArgumentException("Закройте кавычки аргумента");if(started)out.add(part.toString());if(out.size()>100||text.length()>8192)throw new IllegalArgumentException("Слишком много аргументов");return out;}
    public int run(ProcessBuilder builder,int seconds,Output output)throws Exception {
        if(cancelled)throw new CancellationException("Python остановлен");Process p=builder.redirectErrorStream(true).start();process=p;
        if(cancelled)p.destroyForcibly();final java.util.concurrent.atomic.AtomicReference<Exception> failure=new java.util.concurrent.atomic.AtomicReference<>();
        Thread drain=new Thread(()->{try(Reader in=new InputStreamReader(p.getInputStream(),StandardCharsets.UTF_8)){char[] b=new char[4096];int n;String carry="";while((n=in.read(b))!=-1){String s=carry+new String(b,0,n);carry="";if(!s.isEmpty()&&Character.isHighSurrogate(s.charAt(s.length()-1))){carry=s.substring(s.length()-1);s=s.substring(0,s.length()-1);}if(!s.isEmpty())output.write(s);}if(!carry.isEmpty())output.write(carry);}catch(Exception e){failure.set(e);p.destroyForcibly();}},"python-output");drain.setDaemon(true);drain.start();
        try{if(!p.waitFor(seconds,TimeUnit.SECONDS)){p.destroyForcibly();throw new TimeoutException("Истекло время выполнения Python");}drain.join(5000);if(drain.isAlive())throw new IOException("Python завершился, но поток вывода не закрылся");if(cancelled)throw new CancellationException("Python остановлен");if(failure.get()!=null)throw failure.get();return p.exitValue();}finally{p.destroyForcibly();try{p.waitFor(3,TimeUnit.SECONDS);drain.join(3000);if(drain.isAlive()){p.getInputStream().close();drain.interrupt();drain.join(1000);}}catch(InterruptedException e){Thread.currentThread().interrupt();}process=null;}
    }
    public void cancel(){cancelled=true;Process p=process;if(p!=null)p.destroyForcibly();}
    public boolean isCancelled(){return cancelled;}
}
