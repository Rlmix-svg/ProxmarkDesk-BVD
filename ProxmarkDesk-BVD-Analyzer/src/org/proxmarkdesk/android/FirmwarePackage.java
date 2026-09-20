// SPDX-License-Identifier: GPL-3.0-or-later
package org.proxmarkdesk.android;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import org.proxmarkdesk.android.capability.CliCompat;

/** Closed ZIP format; no archive paths are ever used as output paths. */
public final class FirmwarePackage {
    public static final int MAX_IMAGE = 4 * 1024 * 1024;
    public final File directory, fullimage, bootrom;
    public final String version, fullHash, bootHash;
    private FirmwarePackage(File dir, String version, String fullHash, String bootHash) {
        this.directory=dir; this.version=version; this.fullHash=fullHash; this.bootHash=bootHash;
        fullimage=new File(dir,"fullimage.elf");
        bootrom=bootHash.isEmpty()?null:new File(dir,"bootrom.elf");
    }
    public static FirmwarePackage read(InputStream source, File parent) throws Exception {
        Map<String,byte[]> entries=new HashMap<>();
        try(ZipInputStream zip=new ZipInputStream(source)) {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                String name=entry.getName();
                if(entry.isDirectory() || !(name.equals("firmware.properties") || name.equals("fullimage.elf") || name.equals("bootrom.elf")) || entries.containsKey(name))
                    throw new IOException("Пакет содержит лишний или повторный файл: "+name);
                entries.put(name,readLimited(zip,name.equals("firmware.properties")?4096:MAX_IMAGE));
            }
        }
        byte[] metadata=entries.get("firmware.properties"), full=entries.get("fullimage.elf"), boot=entries.get("bootrom.elf");
        if(metadata==null||full==null)throw new IOException("Нужны firmware.properties и fullimage.elf");
        Properties p=new Properties();p.load(new ByteArrayInputStream(metadata));
        String version=p.getProperty("version","");
        if(!"1".equals(p.getProperty("format")) || !"PM3GENERIC".equals(p.getProperty("platform")) || !"AT91SAM7S".equals(p.getProperty("chip")) || !CliCompat.CURRENT_VERSION.equals(version))
            throw new IOException("Нужен пакет PM3GENERIC / AT91SAM7S / "+CliCompat.CURRENT_VERSION);
        String fh=p.getProperty("fullimage.sha256", ""), bh=p.getProperty("bootrom.sha256", "");
        verify(full,fh,false);
        if(boot!=null)verify(boot,bh,true);
        else if(!bh.isEmpty())throw new IOException("Нет bootrom.elf из описания пакета");
        if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Не удалось создать каталог прошивок");
        File dir=new File(parent,UUID.randomUUID().toString());
        if(!dir.mkdir())throw new IOException("Не удалось подготовить пакет");
        try {
            Files.write(new File(dir,"fullimage.elf").toPath(),full);
            if(boot!=null)Files.write(new File(dir,"bootrom.elf").toPath(),boot);
            Files.write(new File(dir,"firmware.properties").toPath(),metadata);
            return new FirmwarePackage(dir,version,fh,bh);
        } catch(Exception e) { for(File f:Objects.requireNonNull(dir.listFiles()))f.delete();dir.delete();throw e; }
    }
    public void recheck() throws Exception {
        verify(Files.readAllBytes(fullimage.toPath()),fullHash,false);
        if(bootrom!=null)verify(Files.readAllBytes(bootrom.toPath()),bootHash,true);
    }
    public static byte[] readLimited(InputStream in,int limit) throws IOException {
        if(in==null)throw new IOException("Не удалось открыть файл");
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
        while((n=in.read(buf))!=-1){if(n>limit-out.size())throw new IOException("Файл слишком большой");out.write(buf,0,n);}return out.toByteArray();
    }
    static String sha256(byte[] bytes) throws Exception {
        StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
    }
    static void verify(byte[] bytes,String hash,boolean boot) throws Exception {
        if(!hash.matches("[0-9a-f]{64}")||!sha256(bytes).equals(hash))throw new IOException("SHA-256 образа не совпадает");
        validateElf(bytes,boot);
    }
    public static void validateElf(byte[] bytes,boolean boot) throws IOException {
        if(bytes.length<52||bytes.length>MAX_IMAGE||bytes[0]!=127||bytes[1]!='E'||bytes[2]!='L'||bytes[3]!='F'||bytes[4]!=1||bytes[5]!=1||bytes[6]!=1)
            throw new IOException("Требуется ELF32 little-endian");
        ByteBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if((b.getShort(16)&65535)!=2||(b.getShort(18)&65535)!=40||b.getInt(20)!=1)throw new IOException("Требуется исполняемый ELF для ARM");
        long table=Integer.toUnsignedLong(b.getInt(28));int size=b.getShort(42)&65535,count=b.getShort(44)&65535;
        if(size!=32||count==0||count>128||table<52||table+(long)size*count>bytes.length)throw new IOException("Повреждена таблица ELF");
        long last=0;int loads=0;
        for(int i=0;i<count;i++) {
            int o=(int)table+i*size;if(b.getInt(o)!=1)continue;
            long offset=Integer.toUnsignedLong(b.getInt(o+4)), addr=Integer.toUnsignedLong(b.getInt(o+12)), len=Integer.toUnsignedLong(b.getInt(o+16)), mem=Integer.toUnsignedLong(b.getInt(o+20));
            if(len==0)continue;
            long low=boot?0x100000L:0x102000L, high=boot?0x102000L:0x180000L;
            if(len!=mem||offset+len>bytes.length||addr<low||addr+len>high||addr<last)throw new IOException("Сегменты ELF не соответствуют "+(boot?"загрузчику":"основной прошивке")+" Proxmark3 Easy");
            loads++;last=addr+len;
        }
        if(loads==0)throw new IOException("ELF не содержит данных для записи");
    }
}
