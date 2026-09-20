package org.proxmarkdesk.android;
import java.io.*;import java.nio.file.*;import java.util.*;import org.json.*;
public class ResourceReadingTests {
 static int checks;static void check(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
 public static void main(String[] args)throws Exception{
  File assets=new File(args[0]),temp=Files.createTempDirectory("pmdesk16-tests-").toFile(),root=new File(temp,"profile"),backup=new File(temp,"backup");
  JSONObject m=new JSONObject(new String(Files.readAllBytes(new File(assets,"resource-manifest.json").toPath()),"UTF-8"));
  String report=ResourceInstaller.install(new FileInputStream(new File(assets,"pm3-data.zip")),m,root,backup);check(report.contains("Resource files OK"),"fresh install");check(new File(root,"lualibs/pm3_cmd.lua").isFile(),"generated commands");check(new File(root,"lualibs/mfc_default_keys.lua").isFile(),"generated defaults");
  File custom=new File(root,"luascripts/user-script.lua");Files.write(custom.toPath(),"-- personal".getBytes("UTF-8"));File changed=new File(root,"lualibs/commands.lua");Files.write(changed.toPath(),"-- modified by user".getBytes("UTF-8"));Files.delete(new File(root,"lualibs/pm3_cmd.lua").toPath());
  report=ResourceInstaller.install(new FileInputStream(new File(assets,"pm3-data.zip")),m,root,backup);check(report.contains("Резервных копий: 1"),"changed stock backed up");check(custom.isFile(),"custom preserved");check(ResourceInstaller.verify(m,root).contains("Resource files OK"),"missing restored");check(Files.walk(backup.toPath()).anyMatch(p->{try{return Files.isRegularFile(p)&&new String(Files.readAllBytes(p),"UTF-8").equals("-- modified by user");}catch(Exception e){return false;}}),"backup contents");
  report=ResourceInstaller.install(new FileInputStream(new File(assets,"pm3-data.zip")),m,root,backup);check(report.contains("Установлено/восстановлено: 0"),"idempotent install");try{ResourceInstaller.child(root,"../escape");throw new AssertionError("traversal");}catch(IOException expected){checks++;}
  CommandCatalog.loadRussian(new FileInputStream(new File(assets,"commands-ru.tsv")));List<ReadingActions.Action> all=ReadingActions.all(CommandCatalog.parse(new FileInputStream(new File(assets,"commands.txt"))),m);
  TagInfo tag=TagInfo.parse(TagInfoTests.HF);List<ReadingActions.Action> selected=ReadingActions.filter(all,tag,"",false,"","Все",false);check(!selected.isEmpty(),"MFU populated");check(selected.stream().noneMatch(x->x.group.equals("hf mf")),"Classic excluded");check(selected.stream().noneMatch(x->x.condition.equals("NTAG")),"generic compatible is not NTAG");check(selected.stream().noneMatch(x->x.condition.equals("Ultra/UL-5")),"Ultralight is not UL5");check(selected.stream().anyMatch(x->x.kind.equals("Lua")),"MFU Lua included");
  check(ReadingActions.filter(all,tag,"",true,"","Все",true).size()==all.size(),"full catalogue");check(ReadingActions.filter(all,null,"",false,"","Все",false).isEmpty(),"unknown no misleading suggestions");check(ReadingActions.filter(all,tag,"",true,"hf_mf_autopwn","Lua",true).size()==1,"live name search");
  for(ReadingActions.Action item:all){check(item.description!=null&&!item.description.isEmpty(),"description "+item.name);if(item.kind.equals("Lua"))check(item.missing.isEmpty(),"dependencies "+item.name);}
  System.out.println("PASS: "+checks+" resource/catalogue checks; actions="+all.size()+"; MFU actions="+selected.size());
 }
}
