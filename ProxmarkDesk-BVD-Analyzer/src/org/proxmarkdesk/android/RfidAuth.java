// SPDX-License-Identifier: GPL-3.0-or-later
package org.proxmarkdesk.android;
import java.util.*;
import java.util.regex.*;
import java.nio.charset.StandardCharsets;

/** ISO14443-A evidence; a password frame alone is not authentication proof. */
public final class RfidAuth {
    public static final String SELECT="hf 14a reader --keep --skip", DROP="hf 14a reader --drop";
    public static class Candidate {
        public String uid="", password="", pack="", state="Нет ответа";
        public boolean confirmed; public int frame;
        public String toString(){return "Кадр "+frame+" · UID "+(uid.isEmpty()?"не установлен":uid)+" · PWD "+password+" · PACK "+(pack.isEmpty()?"—":pack)+" · "+state;}
    }
    public static class Frame { public boolean tag,damaged; public byte[] data; Frame(boolean tag,boolean damaged,byte[] data){this.tag=tag;this.damaged=damaged;this.data=data;} }
    static Matcher match(String re,String s){return Pattern.compile(re).matcher(s);}
    public static String clean(String s){return (s==null?"":s).replaceAll("\\s+","").toUpperCase(Locale.ROOT);}
    public static boolean validUid(String s){return s!=null&&s.matches("(?:[0-9A-F]{8}|[0-9A-F]{14}|[0-9A-F]{20})");}
    public static byte[] bytes(String s){s=clean(s);if(!s.matches("(?:[0-9A-F]{2})*"))throw new IllegalArgumentException("Некорректные HEX-байты");byte[] b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return b;}
    public static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(Locale.ROOT,"%02X",v&255));return s.toString();}
    public static int crc(byte[] b,int length){int crc=0x6363;for(int i=0;i<length;i++){int c=(b[i]&255)^(crc&255);c^=(c<<4)&255;crc=((crc>>8)^(c<<8)^(c<<3)^(c>>4))&65535;}return crc;}
    public static boolean goodCrc(byte[] b){return b.length>=3&&crc(b,b.length-2)==((b[b.length-2]&255)|((b[b.length-1]&255)<<8));}
    public static String uidFromOutput(String output){
        Set<String> ids=new HashSet<>();Matcher m=match("(?im)^\\s*(?:\\[\\+\\]\\s*)?(?:Card selected\\.\\s*)?UID(?:\\[\\d+\\])?\\s*:\\s*((?:[0-9a-f]{2}[ \\t]*){4,10})(?![0-9a-f])",output);
        while(m.find()){String id=clean(m.group(1));if(validUid(id))ids.add(id);}return ids.size()==1?ids.iterator().next():"";
    }
    public static String passwordCommand(String password){password=clean(password);if(!password.matches("[0-9A-F]{8}"))throw new IllegalArgumentException("PWD_AUTH: нужен пароль из 8 HEX-символов");return "hf 14a raw -c -v 1B"+password;}
    public static String selectionError(String expected,String output){expected=clean(expected);if(!validUid(expected))return "Укажите UID найденной метки или выберите запись трассы";String selected=uidFromOutput(output);if(selected.isEmpty())return "Выбор метки не подтверждён. Пароль не отправлен";if(!selected.equals(expected))return "Обнаружена другая метка: "+selected+". Пароль не отправлен";return null;}
    public static String checkReply(String output){
        Matcher m=match("(?im)received\\s+(\\d+)\\s+bytes[^\\r\\n]*\\r?\\n\\s*(?:\\[\\+\\]\\s*)?([0-9a-f][0-9a-f \\t\\[\\]]*)",output);
        if(!m.find())return "Нет подтверждения: ответ PWD_AUTH отсутствует. Это не доказывает неверный пароль";
        byte[] b=bytes(m.group(2).replaceAll("[\\[\\]]",""));
        if(b.length!=Integer.parseInt(m.group(1)))return "Нет подтверждения: неполный ответ PWD_AUTH";
        if(b.length==4&&goodCrc(b))return "Подтверждено: PACK "+hex(Arrays.copyOf(b,2))+", CRC_A OK";
        if(b.length==1)return "Нет подтверждения: короткий ответ / NAK "+hex(b);
        return "Нет подтверждения: PACK без корректного CRC_A";
    }
    public static List<Frame> readTrace(byte[] data,boolean binary){
        if(data.length>8*1024*1024)throw new IllegalArgumentException("Трасса больше 8 МБ");List<Frame> frames=new ArrayList<>();
        if(binary){int pos=0;while(pos<data.length){
            if(pos+8>data.length)throw new IllegalArgumentException("Обрезанный заголовок трассы: "+pos);
            int flags=(data[pos+6]&255)|((data[pos+7]&255)<<8),n=flags&32767,total=8+n+(n+7)/8;
            if(n==0||pos+total>data.length)throw new IllegalArgumentException("Повреждённый кадр трассы: "+pos);
            frames.add(new Frame((flags&32768)!=0,false,Arrays.copyOfRange(data,pos+8,pos+8+n)));pos+=total;
        }}else for(String line:new String(data,StandardCharsets.UTF_8).split("\n")){
            Matcher m=match("(?i)\\|\\s*(Rdr|Tag)\\s*\\|([^|]+)\\|",line);if(!m.find())continue;
            String raw=m.group(2),hex=raw.replaceAll("\\(\\d+\\)","").replace("!","").replace("?","").trim();
            if(!hex.matches("(?:[0-9a-fA-F]{2}\\s*)+"))throw new IllegalArgumentException("Неполная строка трассы: "+line.trim());
            frames.add(new Frame(m.group(1).equalsIgnoreCase("tag"),raw.contains("!")||raw.contains("?"),bytes(hex)));
        }
        if(frames.isEmpty())throw new IllegalArgumentException("Нет кадров ISO14443-A. Нужен .trace или текст hf 14a list");return frames;
    }
    public static List<Candidate> analyze(List<Frame> frames){
        List<Candidate> found=new ArrayList<>();String uid="",parts="";int level=0,pendingLevel=-1;byte[] select=null;Candidate auth=null;
        for(int i=0;i<frames.size();i++){
            Frame f=frames.get(i);byte[] b=f.data;if(b.length==0)continue;int cmd=b[0]&255;
            if(!f.tag){
                auth=null;select=null;pendingLevel=-1;
                if(cmd==0x26||cmd==0x52||cmd==0x50||cmd==0x93){uid="";parts="";level=0;}
                if(cmd==0x93||cmd==0x95||cmd==0x97){
                    uid="";int wanted=(cmd-0x93)/2;
                    if(b.length==9&&b[1]==0x70&&!f.damaged&&goodCrc(b)&&(byte)(b[2]^b[3]^b[4]^b[5])==b[6]&&wanted==level){select=b;pendingLevel=wanted;}
                    else if(wanted!=level||b.length!=2||b[1]!=0x20){parts="";level=0;}
                }
                if(cmd==0x1B&&(b.length==5||b.length==7)){
                    boolean valid=b.length==7&&goodCrc(b)&&!f.damaged;
                    auth=new Candidate();auth.uid=uid;auth.password=hex(Arrays.copyOfRange(b,1,5));auth.frame=i+1;auth.state=valid?"Нет ответа":"PWD_AUTH без корректного CRC_A";
                    found.add(auth);if(!valid)auth=null;
                }
            }else{
                if(select!=null){
                    if(!f.damaged&&b.length==3&&goodCrc(b)){
                        boolean cascade=(b[0]&4)!=0;
                        if(cascade&&(select[2]&255)==0x88&&pendingLevel<2){parts+=hex(Arrays.copyOfRange(select,3,6));level=pendingLevel+1;}
                        else if(!cascade&&(pendingLevel==2||(select[2]&255)!=0x88)){uid=parts+hex(Arrays.copyOfRange(select,2,6));parts="";level=0;}
                        else{parts="";level=0;}
                    }else{parts="";level=0;}
                    select=null;
                }
                if(auth!=null){
                    if(b.length==2||b.length==4)auth.pack=hex(Arrays.copyOf(b,2));
                    auth.confirmed=b.length==4&&goodCrc(b)&&!f.damaged;
                    auth.state=auth.confirmed?"PACK подтверждён CRC_A":b.length==1?"Короткий ответ / NAK":"Ответ без корректного CRC_A";auth=null;
                }
            }
        }return found;
    }
    public static String assessment(String command,String output){
        Matcher auth=match("(?m)^\\[PWD_AUTH\\] (.+)$",output);if(auth.find())return auth.group(1).trim();
        Matcher partial=match("(?i)partial dump[^\\r\\n]*",output);if(partial.find())return "Частичный дамп: "+partial.group();
        if(match("(?i)^hf mfu (rdbl|dump)\\b",command).find()&&!match("\\s(?:-h|--help)\\b",command).find()){
            if(match("(?i)can't select|cannot select|card not found",output).find())return "Метка не выбрана; пароль не проверен";
            if(match("(?i)^hf mfu dump\\b",command).find()&&!match("(?i)saved.*(?:file|\\.bin|\\.json)",output).find())return "Дамп не подтверждён; проверьте вывод";
            if(match("(?i)^hf mfu rdbl\\b",command).find()&&!match("(?im)^.*\\b\\d+(?:/0x[0-9a-f]+)?\\s*\\|\\s*(?:[0-9a-f]{2}\\s+){3}[0-9a-f]{2}",output).find())return "Нет данных блока; пароль не проверен";
        }return null;
    }
}
