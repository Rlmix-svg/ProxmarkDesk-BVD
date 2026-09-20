// SPDX-License-Identifier: GPL-3.0-or-later
package org.proxmarkdesk.android;
import java.util.*;
import java.util.regex.*;

public final class TagInfo {
    public static final String[] FAMILIES={"ISO 14443-A","MIFARE Classic","Ultralight / NTAG","ISO 15693","DESFire","LF EM410x","LF HID","LF T55xx","ISO 14443-B","FeliCa","iCLASS / PicoPass","LEGIC","Topaz","Прочие метки","MIFARE Plus","LF Hitag","LF EM4x05","LF EM4x50"};
    public static final String[] INFOS={"hf 14a info","hf mf info","hf mfu info --noauth","hf 15 info","hf mfdes info","lf em 410x reader","lf hid reader","lf t55xx info","hf 14b info","hf felica info","hf iclass info","hf legic info","hf topaz info","","hf mfp info","lf hitag info","lf em 4x05 info","lf em 4x50 info"};
    public boolean detected,ambiguous;
    public int family=13,classicSize=-1;
    public String id="",memory="",details="",notice="";
    public final LinkedHashMap<String,String> fields=new LinkedHashMap<>();
    public final long timestamp=System.currentTimeMillis();
    public static String clean(String output){
        StringBuilder result=new StringBuilder();
        for(String line:output.replaceAll("\u001b\\[[0-?]*[ -/]*[@-~]","").split("\\R")){
            line=line.replaceFirst("^\\s*\\[[+=!?#|/\\\\-]\\]\\s*","").trim();
            if(line.isEmpty()||line.contains("pm3 -->")||line.contains("PMDESK_END_")||line.startsWith("[ProxmarkDesk]")||line.matches("(?i).*searching for.*")||line.startsWith("Hint:")||line.matches("[|/\\\\-]+"))continue;
            result.append(line).append('\n');
        }
        return result.toString();
    }
    static String match(String pattern,String text){Matcher m=Pattern.compile(pattern,Pattern.CASE_INSENSITIVE|Pattern.MULTILINE).matcher(text);return m.find()?m.group(1).trim():"";}
    public static TagInfo parse(String output){
        TagInfo tag=new TagInfo();tag.details=clean(output);String lower=tag.details.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> ids=new LinkedHashSet<>();
        Matcher uid=Pattern.compile("(?im)^(?:UID|CSN|IDm|Primary IDm)\\s*[:.]+\\s*((?:[0-9a-f]{2}[ \\t]*){4,10})(?=[ \\t]*(?:\\(|$))").matcher(tag.details);
        while(uid.find())ids.add(uid.group(1).replaceAll("\\s","").toUpperCase(Locale.ROOT));
        String em=match("^EM\\s*410x\\s+ID\\s*:?\\s*([0-9a-f]{10})(?:\\s|$)",tag.details);
        if(!em.isEmpty())ids.add(em.toUpperCase(Locale.ROOT));
        String valid=match("^Valid (.+?) found!?$",tag.details);
        tag.detected=!ids.isEmpty()||!valid.isEmpty();
        if(!tag.detected)return tag;
        tag.ambiguous=ids.size()>1;
        if(!ids.isEmpty())tag.id=ids.iterator().next();
        if(lower.contains("mifare plus"))tag.family=14;
        else if(lower.contains("desfire"))tag.family=4;
        else if(lower.contains("ultralight")||lower.contains("ntag"))tag.family=2;
        else if(lower.contains("mifare classic"))tag.family=1;
        else if(lower.contains("iso 15693")||lower.contains("iso15693"))tag.family=3;
        else if(lower.contains("hitag")||valid.toLowerCase(Locale.ROOT).contains("hitag"))tag.family=15;
        else if(lower.contains("em4205")||lower.contains("em4305")||lower.contains("em4469")||lower.contains("em4569")||lower.contains("em4x05"))tag.family=16;
        else if(lower.contains("em4x50"))tag.family=17;
        else if(!em.isEmpty()||valid.toLowerCase(Locale.ROOT).contains("em410x"))tag.family=5;
        else if(valid.toLowerCase(Locale.ROOT).contains("hid prox"))tag.family=6;
        else if(lower.contains("iclass")||lower.contains("picopass"))tag.family=10;
        else if(lower.contains("felica")||lower.contains("idm:"))tag.family=9;
        else if(lower.contains("legic"))tag.family=11;
        else if(lower.contains("topaz"))tag.family=12;
        else if(lower.contains("iso 14443-b")||lower.contains("iso14443-b"))tag.family=8;
        else if(lower.contains("iso 14443-a")||lower.contains("iso14443-a")||lower.contains("atqa:"))tag.family=0;
        if(tag.family==6&&tag.id.isEmpty())tag.id=match("^raw:\\s*([0-9a-f]+)$",tag.details).toUpperCase(Locale.ROOT);
        String bytes=match("\\b([0-9]+)\\s*bytes\\b",tag.details);if(!bytes.isEmpty())tag.memory=bytes+" байт (по данным Iceman)";
        if(tag.family==1){
            if(lower.contains("mini")){tag.classicSize=1;tag.memory="320 байт / 5 секторов";}
            else if(Pattern.compile("(?i)\\b4\\s*k\\b").matcher(tag.details).find()){tag.classicSize=3;tag.memory="4096 байт / 40 секторов";}
            else if(Pattern.compile("(?i)\\b2\\s*k\\b").matcher(tag.details).find()){tag.classicSize=2;tag.memory="2048 байт / 32 секторов";}
            else if(Pattern.compile("(?i)\\b1\\s*k\\b").matcher(tag.details).find()){tag.classicSize=0;tag.memory="1024 байта / 16 секторов";}
        }
        for(String line:tag.details.split("\\R")){
            Matcher field=Pattern.compile("^([A-Za-z][A-Za-z0-9 _/()\\[\\]-]{0,45})\\s*(?::|\\.{2,})\\s*(.+)$").matcher(line);
            if(field.matches())tag.fields.put(field.group(1).trim(),field.group(2).trim());
        }
        if(tag.ambiguous)tag.notice="В выводе несколько разных идентификаторов. Дополнительное чтение не выполняется; оставьте одну метку и повторите поиск.";
        return tag;
    }
    public String automaticInfo(){
        if(!detected||ambiguous||id.isEmpty())return "";
        if(family==1)return "hf 14a info"; // Public identity; mf info also tries keys.
        if(family==5||family==6||family==7||family==13)return ""; // LF search already decoded the ID.
        return INFOS[family];
    }
    public String summary(){
        if(!detected)return "Метка не обнаружена. Предыдущая карточка сброшена.";
        StringBuilder s=new StringBuilder("Обнаружено: "+FAMILIES[family]+"\nUID / ID: "+(id.isEmpty()?"не указан клиентом":id));
        if(!memory.isEmpty())s.append("\nПамять: ").append(memory);
        for(String key:new String[]{"TYPE","ATQA","SAK","UID[0]","Vendor ID","BCC0","BCC1","Lock","OTP","Signature verification","AUTH0","AUTHLIM"})if(fields.containsKey(key))s.append('\n').append(key).append(": ").append(fields.get(key));
        if(!notice.isEmpty())s.append("\n\n").append(notice);
        return s.toString();
    }
    public String report(){return new Date(timestamp)+"\n"+summary()+"\n\nВывод Iceman:\n"+details;}
}
