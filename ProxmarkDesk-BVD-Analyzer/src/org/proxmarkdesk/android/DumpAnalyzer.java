// SPDX-License-Identifier: GPL-3.0-or-later
package org.proxmarkdesk.android;
import java.io.*;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

public final class DumpAnalyzer {
    public static byte[] hex(String s) throws IOException {
        s=s.replaceAll("\\s","");if((s.length()&1)!=0 || !s.matches("[0-9a-fA-F]*"))throw new IOException("Некорректный HEX");
        byte[] b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return b;
    }
    public static byte[] load(File f) throws Exception {
        if(f.length()>8*1024*1024)throw new IOException("Лимит анализа: 8 МиБ");
        byte[] raw=Files.readAllBytes(f.toPath());String name=f.getName().toLowerCase(Locale.ROOT);
        if(name.endsWith(".json")){
            JSONObject blocks=new JSONObject(new String(raw,StandardCharsets.UTF_8)).getJSONObject("blocks");TreeMap<Integer,byte[]> sorted=new TreeMap<>();
            for(Iterator<String> it=blocks.keys();it.hasNext();){String k=it.next();int index=Integer.parseInt(k);if(index<0||sorted.containsKey(index))throw new IOException("Неверный номер блока");sorted.put(index,hex(blocks.getString(k)));}
            ByteArrayOutputStream out=new ByteArrayOutputStream();int n=0,width=-1;
            for(Map.Entry<Integer,byte[]> e:sorted.entrySet()){if(width<0)width=e.getValue().length;if(e.getKey()!=n++||width==0||width!=e.getValue().length)throw new IOException("Пропущены блоки или длины различаются");out.write(e.getValue());}return out.toByteArray();
        }
        if(name.endsWith(".eml")){ByteArrayOutputStream out=new ByteArrayOutputStream();for(String line:new String(raw,StandardCharsets.UTF_8).split("\\R")){line=line.trim();if(!line.isEmpty()&&!line.startsWith("#"))out.write(hex(line));}return out.toByteArray();}
        return raw;
    }
    public static String hexString(byte[] bytes){StringBuilder b=new StringBuilder();for(byte value:bytes)b.append(String.format(Locale.US,"%02X",value&255));return b.toString();}
    public static String describe(byte[] data,int mode) throws Exception {
        StringBuilder out=new StringBuilder("Данные: "+data.length+" байт\nSHA-256: "+hexString(MessageDigest.getInstance("SHA-256").digest(data))+"\n");
        out.append("Полноту чтения проверяйте по журналу операции.\n\n");
        if(mode==1 && data.length>=16){
            byte[] uid={data[0],data[1],data[2],data[4],data[5],data[6],data[7]};out.append("Type 2 UID: ").append(hexString(uid)).append('\n');
            out.append("BCC: ").append(((0x88^(data[0]&255)^(data[1]&255)^(data[2]&255))==(data[3]&255)&&(byte)(data[4]^data[5]^data[6]^data[7])==data[8])?"OK":"не совпадает").append('\n');
            if((data[12]&255)==0xE1)out.append(tlv(Arrays.copyOfRange(data,16,Math.min(data.length,16+(data[14]&255)*8))));else out.append("Стандартный NDEF capability container E1 не найден.\n");
        } else if(mode==2){
            for(int s=0;s<40;s++){int first=s<32?s*4:128+(s-32)*16, count=s<32?4:16, at=(first+count-1)*16;if(at+16>data.length)break;
                int x=data[at+6]&255,y=data[at+7]&255,z=data[at+8]&255;boolean ok=((x&15)^(y>>4))==15&&((x>>4)^(z&15))==15&&((y&15)^(z>>4))==15;
                out.append("Сектор ").append(s).append(" ACL: ").append(ok?"инверсии OK":"ошибка инверсий").append('\n');}
        }
        return out.toString();
    }
    private static String tlv(byte[] d){StringBuilder out=new StringBuilder();int p=0;try{while(p<d.length){int type=d[p++]&255;if(type==254)break;if(type==0)continue;if(p>=d.length)throw new IOException();int n=d[p++]&255;if(n==255){if(p+2>d.length)throw new IOException();n=(d[p++]&255)*256+(d[p++]&255);}if(n>d.length-p)throw new IOException();if(type==3)out.append(ndef(Arrays.copyOfRange(d,p,p+n)));p+=n;}}catch(Exception ex){out.append("Неполная TLV/NDEF-структура\n");}return out.length()==0?"NDEF не найден\n":out.toString();}
    static String ndef(byte[] d){StringBuilder b=new StringBuilder();int p=0;String[] prefixes={"","http://www.","https://www.","http://","https://","tel:","mailto:"};try{while(p<d.length){if(p+2>d.length)throw new IOException();int flags=d[p++]&255,typeLen=d[p++]&255;if((flags&32)!=0)return "Chunked NDEF: используйте декодер Iceman\n";int lenBytes=(flags&16)!=0?1:4;if(p+lenBytes>d.length)throw new IOException();long len=0;for(int i=0;i<lenBytes;i++)len=len*256+(d[p++]&255);int id=0;if((flags&8)!=0){if(p>=d.length)throw new IOException();id=d[p++]&255;}if(len>d.length-p-typeLen-id)throw new IOException();String type=new String(d,p,typeLen,StandardCharsets.US_ASCII);p+=typeLen+id;byte[] payload=Arrays.copyOfRange(d,p,p+(int)len);p+=(int)len;
            b.append("NDEF ").append(type).append(": ");if((flags&7)==1&&type.equals("T")&&payload.length>0){int language=payload[0]&63;if(language+1>payload.length)throw new IOException();b.append(new String(payload,1+language,payload.length-1-language,(payload[0]&128)==0?StandardCharsets.UTF_8:StandardCharsets.UTF_16));}
            else if((flags&7)==1&&type.equals("U")&&payload.length>0){int prefix=payload[0]&255;b.append(prefix<prefixes.length?prefixes[prefix]:"[URI prefix "+prefix+"]").append(new String(payload,1,payload.length-1,StandardCharsets.UTF_8));}else b.append(len).append(" байт");b.append('\n');if((flags&64)!=0)break;}
        }catch(Exception ex){b.append("Неполная NDEF-запись\n");}return b.toString();}
    public static String dump(byte[] data){StringBuilder b=new StringBuilder();for(int i=0;i<Math.min(data.length,65536);i+=16){b.append(String.format(Locale.US,"%06X  ",i));for(int j=i;j<Math.min(i+16,data.length);j++)b.append(String.format(Locale.US,"%02X ",data[j]&255));b.append('\n');}if(data.length>65536)b.append("Предпросмотр: первые 64 КиБ\n");return b.toString();}
    public static String compare(byte[] a,byte[] b){StringBuilder out=new StringBuilder();int differences=0;for(int i=0;i<Math.max(a.length,b.length);i++){if(i<a.length&&i<b.length&&a[i]==b[i])continue;if(differences++<500)out.append(String.format(Locale.US,"%06X  %s → %s\n",i,i<a.length?String.format("%02X",a[i]&255):"--",i<b.length?String.format("%02X",b[i]&255):"--"));}return "Различий: "+differences+" (показано до 500)\n"+out;}
}
