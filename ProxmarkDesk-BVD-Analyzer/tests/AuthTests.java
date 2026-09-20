package org.proxmarkdesk.android;
import java.nio.file.*; import java.util.*;
public class AuthTests {
    static int n;static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);n++;}
    public static void main(String[] args)throws Exception{
        check(RfidAuth.goodCrc(RfidAuth.bytes("5875BD27")),"known CRC_A vector");
        for(String line:Files.readAllLines(Paths.get(args[0],"cases.tsv"))){String[] x=line.split("\t");for(String ext:new String[]{".trace",".txt"}){
            List<RfidAuth.Candidate> found=RfidAuth.analyze(RfidAuth.readTrace(Files.readAllBytes(Paths.get(args[0],x[0]+ext)),ext.equals(".trace")));
            List<String> actual=new ArrayList<>();for(RfidAuth.Candidate c:found)actual.add(c.uid+":"+c.password+":"+(c.confirmed?"1":"0"));check(String.join(";",actual).equals(x[1]),x[0]+ext+" actual="+actual);
        }}
        boolean rejected=false;try{RfidAuth.readTrace(new byte[7],true);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"truncated header");
        byte[] trace=Files.readAllBytes(Paths.get(args[0],"three-uids.trace"));rejected=false;try{RfidAuth.readTrace(Arrays.copyOf(trace,trace.length-1),true);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"truncated parity");
        check(RfidAuth.uidFromOutput("[+] UID: 04 10 20 30 40 50 60 (double)\n[+] UID[0]: 04, NXP").equals("04102030405060"),"UID parser");
        check(RfidAuth.uidFromOutput("[+] UID: 11 22 33 44\n[+] UID: AA BB CC DD").isEmpty(),"ambiguous UID");
        check(RfidAuth.selectionError("11223344","[+] UID: AA BB CC DD")!=null,"different UID rejected");
        check(RfidAuth.selectionError("11223344","Can't select card")!=null,"no selection rejected");
        check(RfidAuth.selectionError("11223344","[+] UID: 11 22 33 44")==null,"matching UID accepted");
        check(RfidAuth.passwordCommand("01 02 03 04").equals("hf 14a raw -c -v 1B01020304"),"dynamic password command");
        check(RfidAuth.checkReply("[+] received 4 bytes\n[+] 58 75 [ BD 27 ]").startsWith("Подтверждено"),"valid reply");
        check(!RfidAuth.checkReply("[+] received 4 bytes\n[+] 58 75 [ 00 00 ]").startsWith("Подтверждено"),"bad CRC");
        check(!RfidAuth.checkReply("").startsWith("Подтверждено"),"silence not success");
        check(RfidAuth.assessment("hf mfu rdbl -b 0 -k 01020304","[usb|script] pm3 --> hf mfu rdbl -b 0 -k 01020304").startsWith("Нет данных"),"silent rdbl");
        check(RfidAuth.assessment("hf mfu dump -k 01020304","").startsWith("Дамп не"),"silent dump");
        check(RfidAuth.assessment("hf mfu dump","[!] Partial dump created. (34 of 41 blocks)").contains("34 of 41"),"partial count");
        check(RfidAuth.assessment("hf mfu rdbl -b 0","[=] 0/0x00 | 11 22 33 44 | ....")==null,"read data");
        System.out.println("PASS auth: "+n);
    }
}
