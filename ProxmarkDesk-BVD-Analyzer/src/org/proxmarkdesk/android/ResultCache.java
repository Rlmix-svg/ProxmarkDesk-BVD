package org.proxmarkdesk.android;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** One durable record per actual client command, including unfinished exchanges. */
public final class ResultCache {
    public final File directory;
    private FileOutputStream stream; private JSONObject active; private String activeId;
    public ResultCache(File root)throws Exception{directory=new File(root,"cache");directory.mkdirs();recover();}
    public static void atomic(File file,String text)throws IOException {
        file.getParentFile().mkdirs();File tmp=new File(file.getParentFile(),file.getName()+"."+UUID.randomUUID()+".tmp");
        try(FileOutputStream out=new FileOutputStream(tmp)){out.write(text.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
        try{Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
    }
    public synchronized void begin(String command,String uid,String device)throws Exception {
        if(active!=null)throw new IOException("Предыдущая запись кэша не завершена");
        activeId=System.currentTimeMillis()+"-"+UUID.randomUUID();
        active=new JSONObject().put("id",activeId).put("command",command).put("uid",uid).put("device",device).put("started",System.currentTimeMillis()).put("status","Выполняется");
        try{atomic(new File(directory,activeId+".json"),active.toString());stream=new FileOutputStream(new File(directory,activeId+".log"));}catch(Exception e){active=null;throw e;}
    }
    public synchronized void append(String line)throws IOException {if(stream!=null){stream.write((line+"\n").getBytes(StandardCharsets.UTF_8));stream.flush();}}
    public synchronized void appendRaw(String text)throws IOException {if(stream!=null){stream.write(text.getBytes(StandardCharsets.UTF_8));stream.flush();}}
    public synchronized void finish(String status)throws Exception {
        if(active==null)return;Exception failure=null;
        try{if(stream!=null){stream.getFD().sync();stream.close();}}catch(Exception e){failure=e;}finally{stream=null;}
        try{active.put("status",failure==null?status:"Ошибка сохранения вывода").put("finished",System.currentTimeMillis());atomic(new File(directory,activeId+".json"),active.toString());}finally{active=null;}
        if(failure!=null)throw failure;
    }
    public void recover()throws Exception {File[] files=directory.listFiles((d,n)->n.endsWith(".json"));if(files!=null)for(File file:files){JSONObject record;try{record=read(file);}catch(Exception e){continue;}if(record.optString("status").equals("Выполняется")){record.put("status","Прервана: приложение завершилось").put("finished",System.currentTimeMillis());atomic(file,record.toString());}}}
    public static JSONObject read(File f)throws Exception {if(f.length()>1024*1024)throw new IOException("Слишком большие метаданные");return new JSONObject(new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));}
    public File log(JSONObject r){String id=r.optString("id");if(!id.matches("[a-zA-Z0-9-]+"))throw new IllegalArgumentException("Неверный ID записи");return new File(directory,id+".log");}
    public static String preview(File f,int limit)throws IOException {try(Reader r=new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8)){char[] b=new char[limit];int n=0,k;while(n<b.length&&(k=r.read(b,n,b.length-n))>0)n+=k;return new String(b,0,n)+(r.read()!=-1?"\n… Предпросмотр ограничен; экспорт содержит полный файл.":"");}}
    public static boolean contains(File f,String query)throws IOException {if(!f.isFile())return false;try(Reader r=new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8)){char[] b=new char[8192];String tail="";int n;while((n=r.read(b))>0){String s=tail+new String(b,0,n).toLowerCase(Locale.ROOT);if(s.contains(query))return true;tail=s.substring(Math.max(0,s.length()-query.length()));}}return false;}
    public List<JSONObject> search(String query)throws Exception {query=query.trim().toLowerCase(Locale.ROOT);List<JSONObject> result=new ArrayList<>();File[] fs=directory.listFiles((d,n)->n.endsWith(".json"));if(fs==null)return result;Arrays.sort(fs,(a,b)->Long.compare(b.lastModified(),a.lastModified()));for(File f:fs){if(Thread.currentThread().isInterrupted())break;try{JSONObject r=read(f);String meta=(r.toString()+" "+CommandCatalog.russian(r.optString("command"))).toLowerCase(Locale.ROOT);if(query.isEmpty()||meta.contains(query)||contains(log(r),query))result.add(r);}catch(Exception ignored){}}return result;}
}
