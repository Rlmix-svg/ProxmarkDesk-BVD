package org.proxmarkdesk.android;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
public class AnalyzerTests {
    static int checks;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}
    interface Action{void run()throws Exception;}
    static void rejected(Action a,String name)throws Exception{try{a.run();}catch(Exception e){check(true,name);return;}throw new AssertionError(name);}
    public static void main(String[] args)throws Exception{
        Path dir=Paths.get(args[0]);Files.createDirectories(dir);
        check(Arrays.equals(DumpAnalyzer.hex("34 C5\nF3"),new byte[]{0x34,(byte)0xc5,(byte)0xf3}),"HEX whitespace");
        rejected(()->DumpAnalyzer.hex("123"),"odd HEX rejected");
        rejected(()->DumpAnalyzer.hex("xz"),"non-HEX rejected");
        byte[] tag=new byte[136];byte[] head=DumpAnalyzer.hex("34C5F38AC95ECD18420078001B2CAD7D");System.arraycopy(head,0,tag,0,16);
        String report=DumpAnalyzer.describe(tag,1);
        check(report.contains("34C5F3C95ECD18"),"user UID preserved");
        check(report.contains("BCC: OK"),"user BCC verified");
        check(report.contains("136"),"partial memory size preserved");
        check(report.contains("E1"),"non-NDEF OTP identified");
        tag[3]^=1;check(DumpAnalyzer.describe(tag,1).contains("не совпадает"),"bad BCC detected");tag[3]^=1;
        check(DumpAnalyzer.describe(new byte[0],0).contains("E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855"),"SHA256 empty vector");
        check(DumpAnalyzer.ndef(DumpAnalyzer.hex("D101055402656E4869")).contains("Hi"),"NDEF Text");
        check(DumpAnalyzer.ndef(DumpAnalyzer.hex("D101045504616263")).contains("https://abc"),"NDEF URI");
        check(DumpAnalyzer.ndef(DumpAnalyzer.hex("D101055402656E48")).contains("Неполная"),"truncated NDEF");
        check(DumpAnalyzer.ndef(DumpAnalyzer.hex("C101FFFFFFFF54")).contains("Неполная"),"NDEF 32-bit length bounds");
        byte[] classic=new byte[64];classic[54]=(byte)255;classic[55]=7;classic[56]=(byte)128;
        check(DumpAnalyzer.describe(classic,2).contains("инверсии OK"),"Classic default ACL");
        classic[54]=0;check(DumpAnalyzer.describe(classic,2).contains("ошибка инверсий"),"Classic invalid ACL");
        check(DumpAnalyzer.compare(new byte[]{1,2},new byte[]{1,3,4}).contains("Различий: 2"),"comparison includes unequal lengths");
        check(DumpAnalyzer.compare(tag,tag).contains("Различий: 0"),"identical comparison");
        Path eml=dir.resolve("sample.eml");Files.write(eml,"# header\n34C5F38A\nC95ECD18\n".getBytes(StandardCharsets.UTF_8));
        check(DumpAnalyzer.load(eml.toFile()).length==8,"EML with comments");
        Path json=dir.resolve("sample.json");Files.write(json,"{\"blocks\":{\"1\":\"C95ECD18\",\"0\":\"34C5F38A\"}}".getBytes(StandardCharsets.UTF_8));
        check(DumpAnalyzer.hexString(DumpAnalyzer.load(json.toFile())).equals("34C5F38AC95ECD18"),"JSON numeric block order");
        Files.write(json,"{\"blocks\":{\"0\":\"00000000\",\"2\":\"00000000\"}}".getBytes(StandardCharsets.UTF_8));
        rejected(()->DumpAnalyzer.load(json.toFile()),"JSON sparse dump rejected");
        Files.write(json,"{\"blocks\":{\"0\":\"00000000\",\"1\":\"00\"}}".getBytes(StandardCharsets.UTF_8));
        rejected(()->DumpAnalyzer.load(json.toFile()),"JSON unequal block widths rejected");
        check(DumpAnalyzer.dump(new byte[65537]).contains("64"),"HEX preview bounded");
        System.out.println("TOTAL "+checks+" checks passed");
    }
}
