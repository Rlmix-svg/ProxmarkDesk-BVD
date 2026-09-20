package org.proxmarkdesk.android;
import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;import org.json.*;
public class BvdTests {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception {
  File dir=new File(args[0]);dir.mkdirs();File f=new File(dir,"tag.eml");Files.write(f.toPath(),"041122BF\n33445566\n44000000\n00000000".getBytes(StandardCharsets.UTF_8));
  JSONObject p=BvdProfiles.create(f,"A\"B","Рабочая","12345678","1234");check(p.getString("uid").equals("04112233445566"),"UID");check(p.getJSONObject("pages").length()==4,"partial pages");File saved=BvdProfiles.save(dir,p);check(BvdProfiles.read(saved).getString("name").equals("A\"B"),"JSON roundtrip");
  JSONObject q=new JSONObject(p.toString());q.getJSONObject("pages").put("3","FFFFFFFF");check(BvdProfiles.compare(Arrays.asList(p,q)).contains("Различающихся страниц: 1"),"compare");
  byte[] a="0 | Rdr | 26 |\n1 | Tag | 44 00 |".getBytes(StandardCharsets.UTF_8);check(BvdProfiles.trace(a,false,a,false).contains("Кадры совпадают"),"same trace");byte[] b="0 | Rdr | 52 |".getBytes(StandardCharsets.UTF_8);check(BvdProfiles.trace(a,false,b,false).contains("позиции 1"),"difference");
  boolean rejected=false;try{BvdProfiles.create(f,"x","Рабочая","123"," ");}catch(Exception e){rejected=true;}check(rejected,"invalid password");System.out.println("PASS: 7 BVD checks");
 }
}
