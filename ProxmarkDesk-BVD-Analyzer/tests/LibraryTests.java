package org.proxmarkdesk.android;
import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import org.json.*;
public class LibraryTests {
    static int n;static void check(boolean b,String m){if(!b)throw new AssertionError(m);n++;}
    public static void main(String[] args)throws Exception{
        File dir=Files.createTempDirectory("pm3-library-").toFile();
        File raw=new File(dir,"dump.bin");byte[] memory=RfidAuth.bytes("041122BF334455664400000000000000");Files.write(raw.toPath(),memory);
        check(DumpLibrary.uid(raw).equals("04112233445566"),"Type2 UID and BCC");
        byte[] header=new byte[72];header[11]=3;System.arraycopy(memory,0,header,56,16);File nativeDump=new File(dir,"native.bin");Files.write(nativeDump.toPath(),header);
        check(DumpLibrary.uid(nativeDump).equals("04112233445566"),"MFU header offset");
        File renamed=DumpLibrary.withUid(nativeDump);check(renamed.getName().startsWith("UID-04112233445566-"),"UID filename");check(DumpLibrary.withUid(renamed).equals(renamed),"rename idempotent");
        File json=new File(dir,"card.json");Files.write(json.toPath(),"{\"Card\":{\"UID\":\"E004010203040506\"}}".getBytes(StandardCharsets.UTF_8));check(DumpLibrary.uid(json).equals("E004010203040506"),"ISO15693 JSON UID");
        boolean blocked=false;try{DumpLibrary.child(dir,"../outside.bin");}catch(IOException e){blocked=true;}check(blocked,"path traversal rejected");
        File classic=new File(dir,"classic.bin");Files.write(classic.toPath(),new byte[1024]);String[] plan=DumpLibrary.emulatorCommands(classic,1,"emulation/abc.bin");check(plan.length==3&&plan[0].equals("hf mf eclr")&&plan[2].equals("hf mf sim --1k"),"Classic load before simulate");
        blocked=false;try{DumpLibrary.emulatorCommands(raw,1,"emulation/abc.bin");}catch(IOException e){blocked=true;}check(blocked,"short Classic rejected");
        check(DumpLibrary.emulatorCommands(renamed,5,"emulation/abc.bin")[2].equals("hf mfu sim -t 7"),"NTAG simulation");
        check(!DumpLibrary.loaded("Done! Hint: hf mfu sim -t 7"),"wrapper hint is not load success");
        check(DumpLibrary.loaded("Uploading to emulator memory\nHint: You are ready to simulate"),"confirmed load");
        check(!DumpLibrary.loaded("Uploading to emulator memory\nYou are ready to simulate\nOnly loaded 2 blocks"),"short upload rejected");
        check(UpdateChecker.compare("v4.21611","v4.9999")>0,"numeric version comparison");
        check(UpdateChecker.compare("v4.21611","Iceman/master/v4.21611-suspect")==0,"suspect release number");
        String release="{\"tag_name\":\"v4.22000\",\"body\":\"Release notes\",\"published_at\":\"2026-09-04\"}";
        check(UpdateChecker.describe(release,"v4.21611","").contains("доступен более новый релиз"),"update available");
        check(UpdateChecker.describe(release,"v4.22000","").contains("не получена"),"unknown firmware not guessed");
        blocked=false;try{UpdateChecker.describe("{\"message\":\"rate limit\"}","v4.21611","");}catch(Exception e){blocked=true;}check(blocked,"API failure never no-update");
        System.out.println("PASS library/update: "+n);
    }
}
