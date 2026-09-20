package org.proxmarkdesk.android;
import java.util.*;
import java.util.regex.*;
import org.json.*;

public final class UiLogic {
    public static List<int[]> commandRanges(String value){
        List<int[]> result=new ArrayList<>();
        Matcher m=Pattern.compile("(?m)^\\[(?:usb|offline)[^\\]\\r\\n]*\\]\\s+pm3 --> ([^\\r\\n]+)$|^\\[ProxmarkDesk\\] [^\\r\\n]* > ([^\\r\\n]+)$").matcher(value);
        while(m.find()){int group=m.start(1)>=0?1:2;if(!m.group(group).startsWith("["))result.add(new int[]{m.start(group),m.end(group)});}
        return result;
    }
    public static int sniffForFamily(int family){switch(family){case 0:case 1:case 2:case 4:return 0;case 8:return 1;case 3:return 2;case 9:return 3;case 10:return 4;case 12:return 5;case 5:case 6:case 7:return 6;default:return -1;}}
    public static String history(String old,String value,String uid,long time)throws JSONException{
        value=RfidAuth.clean(value);uid=RfidAuth.clean(uid);
        if(!value.matches("(?:[0-9A-F]{8}|[0-9A-F]{12}|[0-9A-F]{32})"))return old;
        JSONArray list=new JSONArray(old),next=new JSONArray();
        next.put(new JSONObject().put("value",value).put("uid",uid).put("time",time));
        for(int i=0;i<list.length()&&next.length()<100;i++){JSONObject item=list.getJSONObject(i);if(!item.optString("value").equals(value)||!item.optString("uid").equals(uid))next.put(item);}
        return next.toString();
    }
}
