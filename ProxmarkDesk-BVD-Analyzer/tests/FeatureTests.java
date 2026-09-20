package org.proxmarkdesk.android;
import java.io.*;import java.nio.file.*;import java.util.*;import org.json.*;
public class FeatureTests {
 static int n;static void check(boolean b,String m){if(!b)throw new AssertionError(m);n++;}
 public static void main(String[] args)throws Exception{
  File assets=new File(args[0]);CommandCatalog.loadRussian(new FileInputStream(new File(assets,"commands-ru.tsv")));List<CommandCatalog.Entry> all=CommandCatalog.parse(new FileInputStream(new File(assets,"commands.txt")));
  check(all.size()>900,"full reference catalog");for(CommandCatalog.Entry e:all)check(!e.description.startsWith("Операция клиента"),"Russian description: "+e.command);
  check(!CommandCatalog.filter(all,"чтение","hf mfu","",Collections.emptySet(),false).isEmpty(),"Russian live filter");
  check(CommandCatalog.filter(all,"hf mfu dump","","",Collections.emptySet(),false).stream().anyMatch(e->e.command.equals("hf mfu dump")),"command filter");
  check(CommandCatalog.filter(all,"","","",Collections.singleton("hf mfu dump"),true).size()==1,"favorites");
  check(LibraryMirror.profile("..").equals("Proxmark3-Easy"),"profile traversal");check(LibraryMirror.folder("dumps/x.trace").equals("Трассы"),"trace folder");check(LibraryMirror.folder("classic-keys.bin").equals("Пароли"),"key folder");
  File root=Files.createTempDirectory("pm3-cache-test-").toFile();ResultCache cache=new ResultCache(root);cache.begin("hf mfu info","12345678","Easy 1");cache.append("Частичный ответ");
  check(cache.search("частичный").size()==1,"partial output before completion");cache.finish("Прервана: USB");check(cache.search("12345678").size()==1,"UID search");check(new ResultCache(root).search("USB").size()==1,"persisted disconnect status");
  ResultCache.atomic(new File(cache.directory,"unfinished.json"),new JSONObject().put("id","unfinished").put("command","lf search").put("status","Выполняется").toString());
  ResultCache restored=new ResultCache(root);check(restored.search("приложение завершилось").size()==1,"recover interrupted operation");
  File longLog=new File(root,"long.log");ResultCache.atomic(longLog,String.join("",Collections.nCopies(8190,"x"))+"проверка границы");check(ResultCache.contains(longLog,"проверка границы"),"search spans chunks");
  boolean rejected=false;try{cache.log(new JSONObject().put("id","../secret"));}catch(IllegalArgumentException e){rejected=true;}check(rejected,"cache traversal rejected");
  System.out.println("PASS feature checks: "+n);
 }
}
