package org.proxmarkdesk.android;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

public final class DumpLibrary {
    public static String uid(File f)throws Exception{
        if(f.length()>8*1024*1024)return "";
        String name=f.getName().toLowerCase(Locale.ROOT);
        if(name.endsWith(".json")){JSONObject card=new JSONObject(new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8)).optJSONObject("Card");if(card!=null){String id=RfidAuth.clean(card.optString("UID",""));if(id.matches("[0-9A-F]{8,20}")&&id.length()%2==0)return id;}}
        if(!name.matches(".*\\.(bin|eml|json)$"))return "";
        byte[] b=DumpAnalyzer.load(f);
        String id=type2(b,0);if(!id.isEmpty())return id;
        if(b.length>=68&&b.length==56+4*((b[11]&255)+1)){id=type2(b,56);if(!id.isEmpty())return id;}
        if((b.length==320||b.length==1024||b.length==2048||b.length==4096)&&(byte)(b[0]^b[1]^b[2]^b[3])==b[4])return RfidAuth.hex(Arrays.copyOf(b,4));
        return "";
    }
    static String type2(byte[] b,int p){if(b.length<p+12)return "";if((byte)(0x88^b[p]^b[p+1]^b[p+2])!=b[p+3]||(byte)(b[p+4]^b[p+5]^b[p+6]^b[p+7])!=b[p+8])return "";return RfidAuth.hex(new byte[]{b[p],b[p+1],b[p+2],b[p+4],b[p+5],b[p+6],b[p+7]});}
    public static File child(File root,String name)throws IOException{
        if(name==null||name.isEmpty()||name.length()>160||name.equals(".")||name.equals("..")||name.matches(".*[\\\\/\\r\\n\\x00].*"))throw new IOException("Недопустимое имя файла");
        File f=new File(root,name);if(!f.getCanonicalFile().getParentFile().equals(root.getCanonicalFile()))throw new IOException("Файл вне библиотеки");return f;
    }
    public static File withUid(File f)throws Exception{
        String id=uid(f);if(id.isEmpty())return f;
        String name=f.getName().replaceFirst("^UID-(?:[0-9A-F]+|unknown)-","");
        File dest=child(f.getParentFile(),"UID-"+id+"-"+name);
        if(dest.equals(f))return f;if(dest.exists())throw new IOException("Файл с таким UID и именем уже существует");Files.move(f.toPath(),dest.toPath());return dest;
    }
    public static String extension(File file)throws IOException{String n=file.getName().toLowerCase(Locale.ROOT);for(String ext:new String[]{".bin",".json",".eml"})if(n.endsWith(ext))return ext;throw new IOException("Для эмуляции нужен BIN / JSON / EML дамп");}
    public static String[] emulatorCommands(File f,int mode,String path)throws Exception{
        if(!f.isFile()||f.length()==0||f.length()>8*1024*1024)throw new IOException("Пустой или слишком большой файл эмуляции");
        if(!path.matches("emulation/[a-f0-9]+\\.(bin|eml|json)"))throw new IOException("Неверный путь эмуляции");extension(f);
        if(mode<0||mode>8)throw new IOException("Выберите тип эмуляции");
        if(mode<4){String[] sizes={"mini","1k","2k","4k"};int[] bytes={320,1024,2048,4096};if(DumpAnalyzer.load(f).length!=bytes[mode])throw new IOException("Classic "+sizes[mode]+": требуется "+bytes[mode]+" байт памяти");return new String[]{"hf mf eclr","hf mf eload --"+sizes[mode]+" -f "+path,"hf mf sim --"+sizes[mode]};}
        if(mode<8){int[] types={2,7,13,14};byte[] data=DumpAnalyzer.load(f);if(data.length<16||data.length%4!=0)throw new IOException("Неверный размер MFU-дампа");return new String[]{"hf mf eclr","hf mfu eload -f "+path,"hf mfu sim -t "+types[mode-4]};}
        return new String[]{"hf 15 eload -f "+path,"hf 15 sim"};
    }
    public static boolean loaded(String output){String s=output.toLowerCase(Locale.ROOT);return s.contains("uploading to emulator memory")&&(s.contains("you are ready to simulate")||s.contains("bytes to emulator memory"))&&!s.matches("(?s).*(?:\\[-\\]|\\[!!\\]|error|failed|can't|cannot|only loaded|of expected|timeout|timed out).*");}
}
