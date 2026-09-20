package org.proxmarkdesk.android;
import java.util.*;
import org.json.*;
public class UiLogicTests {
    static int n;static void check(boolean value,String label){if(!value)throw new AssertionError(label);n++;}
    public static void main(String[] args)throws Exception{
        String log="[ProxmarkDesk] date > hf search\n[usb|script] pm3 --> hf search\n[+] UID: 11 22 33 44\n[=] example pm3 --> not a command\n[offline|script] pm3 --> hw version\n[ProxmarkDesk] date > [подключение]\n";
        List<int[]> ranges=UiLogic.commandRanges(log);check(ranges.size()==3,"only actual commands have color ranges");
        check(log.substring(ranges.get(0)[0],ranges.get(0)[1]).equals("hf search"),"header prefix excluded");
        check(log.substring(ranges.get(1)[0],ranges.get(1)[1]).equals("hf search"),"client prefix excluded");
        check(log.substring(ranges.get(2)[0],ranges.get(2)[1]).equals("hw version"),"offline command");
        check(UiLogic.commandRanges("[+] received 4 bytes\n[+] 58 75 [ BD 27 ]").isEmpty(),"results never colored");
        check(UiLogic.sniffForFamily(2)==0&&UiLogic.sniffForFamily(1)==0,"NTAG and Classic use ISO A");
        check(UiLogic.sniffForFamily(8)==1&&UiLogic.sniffForFamily(3)==2,"B and 15693");
        check(UiLogic.sniffForFamily(9)==3&&UiLogic.sniffForFamily(10)==4&&UiLogic.sniffForFamily(12)==5,"Felica iCLASS Topaz");
        check(UiLogic.sniffForFamily(5)==6&&UiLogic.sniffForFamily(7)==6,"LF capture");
        check(UiLogic.sniffForFamily(11)==-1&&UiLogic.sniffForFamily(13)==-1,"unsupported protocol never guessed");
        String history=UiLogic.history("[]","01 02 03 04","11 22 33 44",1);
        check(new JSONArray(history).getJSONObject(0).getString("value").equals("01020304"),"normalized password");
        history=UiLogic.history(history,"01020304","11223344",2);
        check(new JSONArray(history).length()==1,"same UID password deduplicated");
        history=UiLogic.history(history,"01020304","AABBCCDD",3);
        check(new JSONArray(history).length()==2,"same password different UID kept separate");
        check(UiLogic.history(history,"bad","",4).equals(history),"incomplete password ignored");
        for(int i=0;i<120;i++)history=UiLogic.history(history,String.format("%08X",i),"11223344",i+5);
        check(new JSONArray(history).length()==100,"history bounded");
        check(new JSONArray(history).getJSONObject(0).getString("value").equals("00000077"),"newest first");
        System.out.println("PASS UI logic: "+n);
    }
}
