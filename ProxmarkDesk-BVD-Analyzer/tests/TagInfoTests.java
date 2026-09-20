package org.proxmarkdesk.android;
import java.util.*;
public class TagInfoTests {
    static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    static final String HF="[=] ---------- ISO14443-A Information ----------\n[+] UID: 34 C5 F3 C9 5E CD 18   ( double )\n[+] ATQA: 00 44\n[+] SAK: 00 [2]\n[+]       Mikron JSC Russia\n[+] Possible types:\n[+] TYPE: MIFARE Ultralight EV1 128bytes (MF0UL2101)\n[+] MIFARE Ultralight/C/NTAG Compatible\n[+] Valid ISO 14443-A tag found\n[]Searching for LEGIC tag...\n[|] Searching for FeliCa tag...\n";
    static final String MFU="[=] --- Tag Information ---\n[+] TYPE: MIFARE Ultralight EV1 128bytes (MF0UL2101)\n[+] UID: 34 C5 F3 C9 5E CD 18\n[+] UID[0]: 34, Mikron JSC Russia\n[+] BCC0: 8A ( ok )\n[+] BCC1: 42 ( ok )\n[+] Lock: 78 00\n[+] OTP: 1B 2C AD 7D\n[=] --- Tag Counters\n[=] [0]: 00 00 00\n[+] Signature verification: failed\n[=] Size: 0E, (128 bytes)\n";
    public static void main(String[] args)throws Exception{
        TagInfo t=TagInfo.parse(HF);check(t.detected&&t.family==2,"user Mikron tag selects Ultralight");
        check(t.id.equals("34C5F3C95ECD18"),"seven-byte UID preserved");
        check(t.memory.startsWith("128"),"memory from Iceman type");
        check(t.fields.get("ATQA").equals("00 44"),"ATQA parsed");
        check(t.automaticInfo().equals("hf mfu info --noauth"),"automatic MFU disables default key attempts");
        List<String> calls=new ArrayList<>();t=AutoInspect.collect(HF,c->{calls.add(c);return MFU;});
        check(calls.size()==1,"exactly one MFU enrichment command");
        check(t.summary().contains("Mikron")&&t.summary().contains("BCC0"),"manufacturer and BCC included");
        check(t.report().contains("Tag Counters")&&t.report().contains("Signature verification: failed"),"full counters and signature results preserved");
        check(!TagInfo.parse("[-] No known 125/134 kHz tags found!\n[!] No known/supported 13.56 MHz tags found\n[|] Searching for ISO14443-A tag...").detected,"failed searches do not detect a tag");
        check(!TagInfo.parse("[?] Hint: Try `hf mfu info`\n[usb|script] pm3 --> hf mfu info -h\nTYPE: MIFARE Ultralight").detected,"help text and hints do not detect a tag");
        String em="[+] EM 410x ID 000043C6C0\n[+] EM410x ( RF/64 )\n[+] Unique TAG ID : 0000C26303\n[+] DEZ 10 : 0004441792\n[+] Valid EM410x ID found!\n[=] Couldn't identify a chipset\n";
        t=AutoInspect.collect(em,c->{throw new AssertionError("LF must not trigger HF reading");});
        check(t.family==5&&t.id.equals("000043C6C0"),"LF EM410x ID auto-selected");
        check(!t.ambiguous&&t.report().contains("RF/64"),"EM alternate ID is not a second tag");
        String classic="[+] UID: 01 02 03 04\n[+] ATQA: 00 04\n[+] SAK: 08 [2]\n[+] MIFARE Classic 1K\n[+] Valid ISO 14443-A tag found\n";
        t=TagInfo.parse(classic);check(t.family==1&&t.classicSize==0,"Classic 1K family and size");
        check(t.automaticInfo().equals("hf 14a info"),"Classic automatic info avoids mf key testing");
        check(TagInfo.parse(classic.replace("1K","4K")).classicSize==3,"Classic 4K size");
        String generic=classic.replace("MIFARE Classic 1K","Unknown manufacturer");calls.clear();
        t=AutoInspect.collect(generic,c->{calls.add(c);return calls.size()==1?HF.replace("34 C5 F3 C9 5E CD 18","01 02 03 04"):MFU.replace("34 C5 F3 C9 5E CD 18","01 02 03 04");});
        check(calls.equals(Arrays.asList("hf 14a info","hf mfu info --noauth"))&&t.family==2,"generic ISO-A refined in bounded two steps");
        t=AutoInspect.collect(HF,c->MFU.replace("34 C5 F3 C9 5E CD 18","04 11 22 33 44 55 66"));
        check(t.notice.contains("изменился")&&!t.fields.containsKey("BCC0"),"changed tag data never merged");
        t=AutoInspect.collect(HF,c->"[-] No tag found\n");check(t.detected&&t.notice.contains("не подтверждены"),"removed tag preserves initial result with notice");
        t=AutoInspect.collect(HF,c->{throw new java.io.IOException("USB disconnected");});check(t.detected&&t.notice.contains("прервано"),"transport failure preserves initial result");
        t=TagInfo.parse(HF+"[+] UID: 04 11 22 33 44 55 66\n");check(t.ambiguous&&t.automaticInfo().isEmpty(),"multiple UIDs disable enrichment");
        t=TagInfo.parse("[+] UID: E0 04 01 02 03 04 05 06\n[+] Valid ISO 15693 tag found\n");check(t.family==3&&t.automaticInfo().equals("hf 15 info"),"ISO15693 routing");
        t=TagInfo.parse("[+] IDm: 0102030405060708\n[+] Valid ISO 18092 / FeliCa tag found\n");check(t.family==9,"FeliCa routing");
        t=TagInfo.parse("[+] CSN: 01 02 03 04 05 06 07 08\n[+] Valid iCLASS tag / PicoPass tag found\n");check(t.family==10,"iCLASS routing");
        t=TagInfo.parse(classic.replace("MIFARE Classic 1K","MIFARE DESFire EV2"));check(t.family==4,"DESFire routing");
        t=TagInfo.parse(classic.replace("MIFARE Classic 1K","MIFARE Plus 2K"));check(t.family==14&&t.automaticInfo().equals("hf mfp info"),"MIFARE Plus routing");
        t=TagInfo.parse("[+] UID: 01 02 03 04\n[+] Valid Hitag tag found\n");check(t.family==15&&t.automaticInfo().equals("lf hitag info"),"Hitag routing");
        t=TagInfo.parse("[+] UID: 01 02 03 04\n[+] EM4305 / EM4x05\n");check(t.family==16&&t.automaticInfo().equals("lf em 4x05 info"),"EM4x05 routing");
        check(AutoInspect.isSearch(" HF   SEARCH "),"search normalized");
        check(!AutoInspect.isSearch("lf search -1")&&!AutoInspect.isSearch("hf search -h"),"offline buffer and help do not start RF info");
        t=AutoInspect.collect("No tags",c->{throw new AssertionError();});check(!t.detected&&t.summary().contains("сброшена"),"no tag clears previous card presentation");
        check(TagInfo.parse("[+] Valid FDX-B ID found!\n").detected,"unsupported families retain discovery result");
        System.out.println("TOTAL "+checks+" tag checks passed");
    }
}
