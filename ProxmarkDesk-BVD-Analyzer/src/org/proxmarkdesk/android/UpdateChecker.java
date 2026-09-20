package org.proxmarkdesk.android;
import org.proxmarkdesk.android.capability.CliCompat;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;
import org.json.*;

public final class UpdateChecker {
    public static final String ENDPOINT="https://api.github.com/repos/RfidResearchGroup/proxmark3/releases/latest";
    public static final String RELEASES="https://github.com/RfidResearchGroup/proxmark3/releases";
    public static String version(String text){Matcher m=Pattern.compile("v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?").matcher(text);return m.find()?m.group():"";}
    public static int compare(String a,String b){String[] x=version(a).replaceFirst("^v","").split("\\."),y=version(b).replaceFirst("^v","").split("\\.");if(x.length<2||y.length<2)throw new IllegalArgumentException("Версия не распознана");for(int i=0;i<Math.max(x.length,y.length);i++){long u=i<x.length?Long.parseLong(x[i]):0,v=i<y.length?Long.parseLong(y[i]):0;if(u!=v)return Long.compare(u,v);}return 0;}
    public static String describe(String json,String client,String firmware)throws Exception{
        JSONObject o=new JSONObject(json);String latest=o.getString("tag_name");if(version(latest).isEmpty()||o.optBoolean("draft")||o.optBoolean("prerelease"))throw new IOException("Ответ не содержит стабильного релиза");
        StringBuilder s=new StringBuilder("Последний стабильный Iceman: "+latest+"\nОпубликован: "+o.optString("published_at","не указано")+"\n");
        for(String[] current:new String[][]{{"Встроенный клиент",client},{"Прошивка устройства",firmware}}){s.append(current[0]).append(": ").append(current[1].isEmpty()?"не получена":current[1]);if(!version(current[1]).isEmpty()){int cmp=compare(latest,current[1]);s.append(cmp>0?" — доступен более новый релиз":cmp==0?" — версия совпадает со стабильным релизом":" — новее стабильного релиза");}s.append('\n');}
        s.append("\nСравниваются номера стабильных релизов, не git-коммиты master/suspect.\nДля Easy нужна сборка PM3 GENERIC. Клиент и прошивку обновляйте согласованно. Проверка не прошивает устройство.\n\n").append(o.optString("body",""));return s.toString();
    }
    public static String fetch()throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(ENDPOINT).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","ProxmarkDesk-Android");c.setRequestProperty("Accept","application/vnd.github+json");
        try{int code=c.getResponseCode();if(code!=200)throw new IOException("GitHub HTTP "+code+(code==403||code==429?" — лимит запросов; повторите позже":""));try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>2*1024*1024)throw new IOException("Слишком большой ответ GitHub");out.write(b,0,n);}String json=new String(out.toByteArray(),StandardCharsets.UTF_8);new JSONObject(json).getString("tag_name");return json;}}finally{c.disconnect();}
    }
    public static void main(String[] args)throws Exception{String json=fetch();System.out.println(describe(json,CliCompat.CURRENT_VERSION,""));}
}
