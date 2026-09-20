package org.proxmarkdesk.android;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Saved observations; missing pages stay missing and do not imply access status. */
public final class BvdProfiles {
    public static JSONObject create(File file,String name,String outcome,String pwd,String pack)throws Exception {
        byte[] data=DumpAnalyzer.load(file);int offset=0;
        if(file.getName().endsWith(".bin")&&data.length>=65&&(data.length-56)%4==0&&bcc(data,56))offset=56;
        if(!bcc(data,offset)||((data.length-offset)%4)!=0)throw new IOException("Нужен дамп Type 2 с корректными UID/BCC и страницами по 4 байта");
        if(!pwd.isEmpty()&&!pwd.matches("[0-9A-Fa-f]{8}"))throw new IOException("PWD: 8 HEX");
        if(!pack.isEmpty()&&!pack.matches("[0-9A-Fa-f]{4}"))throw new IOException("PACK: 4 HEX");
        byte[] id={data[offset],data[offset+1],data[offset+2],data[offset+4],data[offset+5],data[offset+6],data[offset+7]};
        JSONObject pages=new JSONObject();for(int i=offset;i<data.length;i+=4)pages.put(""+((i-offset)/4),DumpAnalyzer.hexString(Arrays.copyOfRange(data,i,i+4)));
        String signature=offset==56?DumpAnalyzer.hexString(Arrays.copyOfRange(data,12,44)):"";
        if(file.getName().endsWith(".json")){JSONObject src=new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));JSONObject card=src.optJSONObject("Card");if(card!=null)signature=card.optString("Signature",signature);}
        return new JSONObject().put("schema","bvd-profile-1").put("uid",DumpAnalyzer.hexString(id)).put("name",name).put("outcome",outcome).put("pwd",pwd.toUpperCase(Locale.ROOT)).put("pack",pack.toUpperCase(Locale.ROOT)).put("signature",signature).put("source",file.getName()).put("created",System.currentTimeMillis()).put("pages",pages);
    }
    static boolean bcc(byte[] d,int o){return d.length>=o+9&&(0x88^(d[o]&255)^(d[o+1]&255)^(d[o+2]&255))==(d[o+3]&255)&&(byte)(d[o+4]^d[o+5]^d[o+6]^d[o+7])==d[o+8];}
    public static File save(File dir,JSONObject p)throws Exception {dir.mkdirs();File f=new File(dir,"UID-"+p.getString("uid")+"-"+UUID.randomUUID()+".json");Files.write(f.toPath(),p.toString(2).getBytes(StandardCharsets.UTF_8));return f;}
    public static JSONObject read(File f)throws Exception{return new JSONObject(new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));}
    public static String report(JSONObject p)throws Exception {StringBuilder b=new StringBuilder();for(String k:new String[]{"name","uid","outcome","pwd","pack","signature","source"})b.append(k).append(": ").append(p.optString(k)).append('\n');JSONObject pages=p.getJSONObject("pages");b.append("Страниц: ").append(pages.length()).append("\n\n");for(int i:indices(pages))b.append(String.format(Locale.ROOT,"%03d / %02X: %s%n",i,i,pages.getString(""+i)));return b.append("\nРезультат работы на домофоне задаётся пользователем. PWD/PACK и отдельные байты не доказывают право доступа. Непрочитанные страницы отсутствуют.").toString();}
    static SortedSet<Integer> indices(JSONObject p){SortedSet<Integer>s=new TreeSet<>();Iterator<String>it=p.keys();while(it.hasNext())s.add(Integer.parseInt(it.next()));return s;}
    public static String compare(List<JSONObject> profiles)throws Exception {if(profiles.size()<2)throw new IOException("Нужны минимум два профиля");SortedSet<Integer> pages=new TreeSet<>();StringBuilder b=new StringBuilder("Сравнение профилей\n");for(JSONObject p:profiles){pages.addAll(indices(p.getJSONObject("pages")));b.append(p.optString("name")).append(" · ").append(p.optString("uid")).append(" · ").append(p.optString("outcome")).append('\n');}int count=0;for(int i:pages){Set<String>v=new HashSet<>();for(JSONObject p:profiles)v.add(p.getJSONObject("pages").optString(""+i,"нет данных"));if(v.size()<2)continue;count++;b.append("\nСтраница ").append(i).append('\n');for(JSONObject p:profiles)b.append(p.optString("uid")).append(": ").append(p.getJSONObject("pages").optString(""+i,"нет данных")).append('\n');}return b.append("\nРазличающихся страниц: ").append(count).append(". Различия — наблюдения; назначение байтов требует проверки по RF-трассам.").toString();}
    public static String trace(byte[] a,boolean binary,byte[] other,boolean otherBinary){List<RfidAuth.Frame> frames=RfidAuth.readTrace(a,binary);StringBuilder b=new StringBuilder("Кадров: "+frames.size()+"\n");if(other!=null){List<RfidAuth.Frame> second=RfidAuth.readTrace(other,otherBinary);int i=0;while(i<Math.min(frames.size(),second.size())&&equal(frames.get(i),second.get(i)))i++;b.append(i==frames.size()&&i==second.size()?"Кадры совпадают":"Первое различие на позиции "+(i+1)+"; кадров A/B: "+frames.size()+"/"+second.size()).append("\nСравнение по позиции: повтор или пропуск может сдвинуть последующие кадры.\n");for(int j=Math.max(0,i-2);j<Math.min(Math.max(frames.size(),second.size()),i+5);j++)b.append(j+1).append(" A: ").append(j<frames.size()?frame(frames.get(j)):"—").append("\n  B: ").append(j<second.size()?frame(second.get(j)):"—").append('\n');}else {for(int i=0;i<Math.min(500,frames.size());i++)b.append(i+1).append(" ").append(frame(frames.get(i))).append('\n');b.append("\nPWD_AUTH:\n");for(RfidAuth.Candidate c:RfidAuth.analyze(frames))b.append(c).append('\n');}return b.toString();}
    static boolean equal(RfidAuth.Frame a,RfidAuth.Frame b){return a.tag==b.tag&&a.damaged==b.damaged&&Arrays.equals(a.data,b.data);}
    static String frame(RfidAuth.Frame f){return (f.tag?"TAG ":"RDR ")+RfidAuth.hex(f.data)+(f.damaged?" [повреждён]":"");}
}
